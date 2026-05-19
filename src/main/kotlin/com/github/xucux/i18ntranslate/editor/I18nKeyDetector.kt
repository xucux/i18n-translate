package com.github.xucux.i18ntranslate.editor

import com.github.xucux.i18ntranslate.file.MessageFileKind
import com.intellij.openapi.editor.Editor
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * 在 json/yaml/properties 行内解析光标下的「属性 key」，以及普通源码中取词作为待写入 key。
 */
object I18nKeyDetector {

    /** 当前行解析出的逻辑 key 与可选 value（仅用于确认/预填弹窗）。 */
    data class KeyAtCaret(val key: String, val value: String?)

    /**
     * 若当前文件为 i18n 资源且光标在 key 上，解析 key 与 value。
     */
    fun detectKey(editor: Editor, content: CharSequence, path: String): KeyAtCaret? {
        val ext = runCatching { Paths.get(path).extension.lowercase() }.getOrNull() ?: return null
        val kind = when (ext) {
            "json" -> MessageFileKind.JSON
            "properties" -> MessageFileKind.PROPERTIES
            "yaml", "yml" -> MessageFileKind.YAML
            else -> return null
        }
        val offset = editor.caretModel.offset
        val lineStart = editor.document.getLineStartOffset(editor.document.getLineNumber(offset))
        val lineEnd = editor.document.getLineEndOffset(editor.document.getLineNumber(offset))
        val line = content.subSequence(lineStart, lineEnd.coerceAtMost(content.length)).toString()

        return when (kind) {
            MessageFileKind.PROPERTIES -> parsePropertiesLine(line, offset - lineStart)
            MessageFileKind.YAML -> parseYamlLine(line, offset - lineStart)
            MessageFileKind.JSON -> parseJsonLine(line, offset - lineStart, content, lineStart)
        }
    }

    /** `key= value` / `key: value`，光标须在 key 区间内。 */
    private fun parsePropertiesLine(line: String, col: Int): KeyAtCaret? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null
        val sepIdx = line.indexOfAny(charArrayOf('=', ':'))
        if (sepIdx < 0) return null
        val key = line.substring(0, sepIdx).trim()
        if (key.isEmpty()) return null
        if (col !in 0 until sepIdx) return null
        val value = line.substring(sepIdx + 1).trim().trim('"')
        return KeyAtCaret(key, value)
    }

    /** `key: value` 扁平映射行，光标须在 key 上。 */
    private fun parseYamlLine(line: String, col: Int): KeyAtCaret? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val key = trimmed.substring(0, colon).trim()
        if (key.isEmpty()) return null
        val keyStartInLine = line.indexOf(key)
        val keyEnd = keyStartInLine + key.length
        if (col < keyStartInLine || col > keyEnd) return null
        var valuePart = trimmed.substring(colon + 1).trim()
        if (valuePart.startsWith('"') && valuePart.endsWith('"') && valuePart.length >= 2) {
            valuePart = valuePart.substring(1, valuePart.length - 1)
        }
        return KeyAtCaret(key, valuePart)
    }

    /** 单行 `"key": "value"`（或右值为非字符串字面量），光标须在 key 的引号内。 */
    private fun parseJsonLine(line: String, col: Int, full: CharSequence, lineStart: Int): KeyAtCaret? {
        val m = Regex("""^\s*"([^"\\]+(?:\\.[^"\\]*)*)"\s*:\s*"([^"]*)"\s*,?\s*$""").find(line)
            ?: Regex("""^\s*"([^"\\]+(?:\\.[^"\\]*)*)"\s*:\s*([^,]+)\s*,?\s*$""").find(line)
        if (m == null) return null
        val key = m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        val keyStart = line.indexOf('"')
        val keyOpenEnd = line.indexOf('"', keyStart + 1)
        if (col < keyStart || col > keyOpenEnd) return null
        val rawVal = m.groupValues.getOrNull(2)?.trim().orEmpty()
        val value = rawVal.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        return KeyAtCaret(key, value)
    }

    /**
     * 在源文件已排序的 key 列表中，返回 [currentKey] 的前一项（用于目标文件插入锚点）。
     */
    fun previousKeyInSameFile(
        orderedKeys: List<String>,
        currentKey: String,
    ): String? {
        val i = orderedKeys.indexOf(currentKey)
        if (i <= 0) return null
        return orderedKeys[i - 1]
    }

    /** 优先选区；无选区则按光标扩展为「单词」（字母数字与 `_ . -」）。 */
    fun selectedTextOrWord(editor: Editor): String? {
        val s = editor.selectionModel.selectedText?.trim()?.takeIf { it.isNotEmpty() }
        if (s != null) return s
        val doc = editor.document
        val offset = editor.caretModel.offset
        val text = doc.charsSequence
        if (offset < 0 || offset > text.length) return null
        var start = offset
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = offset
        while (end < text.length && isWordChar(text[end])) end++
        if (start == end) return null
        return text.subSequence(start, end).toString()
    }

    /** [selectedTextOrWord] 无选区时扩展光标左右所使用的「词」字符集。 */
    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '.' || c == '-'

    /** 选区中解析出的 key 及其实际 value。 */
    data class SelectedKeyValue(val key: String, val value: String)

    /**
     * 从编辑器选区中提取所有有效 key-value 对（逐行按资源文件格式解析）。
     * 值直接从选区文本中获取，无需读盘，支持未保存的编辑器内容。
     */
    fun selectedKeyValues(editor: Editor, path: String): List<SelectedKeyValue> {
        val selectedText = editor.selectionModel.selectedText?.trim() ?: return emptyList()
        val ext = runCatching { Paths.get(path).extension.lowercase() }.getOrNull() ?: return emptyList()
        val kind = when (ext) {
            "json" -> MessageFileKind.JSON
            "properties" -> MessageFileKind.PROPERTIES
            "yaml", "yml" -> MessageFileKind.YAML
            else -> return emptyList()
        }
        return selectedText.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return@mapNotNull null
            try {
                when (kind) {
                    MessageFileKind.PROPERTIES -> {
                        val sepIdx = line.indexOfAny(charArrayOf('=', ':'))
                        if (sepIdx < 0) null else {
                            val key = line.substring(0, sepIdx).trim()
                            val value = line.substring(sepIdx + 1).trim().trim('"')
                            if (key.isEmpty()) null else SelectedKeyValue(key, value)
                        }
                    }
                    MessageFileKind.YAML -> {
                        val startTrimmed = line.trimStart()
                        val colon = startTrimmed.indexOf(':')
                        if (colon <= 0) null else {
                            val key = startTrimmed.substring(0, colon).trim()
                            var value = startTrimmed.substring(colon + 1).trim()
                            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                                value = value.substring(1, value.length - 1)
                            }
                            if (key.isEmpty()) null else SelectedKeyValue(key, value)
                        }
                    }
                    MessageFileKind.JSON -> {
                        val m = Regex("""^\s*"([^"\\]+(?:\\.[^"\\]*)*)"\s*:\s*"([^"]*)"\s*,?\s*$""").find(line)
                            ?: Regex("""^\s*"([^"\\]+(?:\\.[^"\\]*)*)"\s*:\s*([^,]+)\s*,?\s*$""").find(line)
                        if (m == null) null else {
                            val key = m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
                            val rawVal = m.groupValues.getOrNull(2)?.trim().orEmpty()
                            val value = rawVal.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                            SelectedKeyValue(key, value)
                        }
                    }
                }
            } catch (_: Exception) { null }
        }.distinctBy { it.key }
    }

    /**
     * 从编辑器选区中提取所有有效 key 的列表（仅 key，不取值）。
     */
    fun selectedKeys(editor: Editor, path: String): List<String> =
        selectedKeyValues(editor, path).map { it.key }
}
