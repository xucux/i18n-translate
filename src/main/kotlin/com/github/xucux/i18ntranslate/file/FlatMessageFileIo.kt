package com.github.xucux.i18ntranslate.file

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.Properties

/**
 * 单层 key-value 资源文件读写（json / properties / yaml），不允许嵌套对象。
 */
object FlatMessageFileIo {

    private val jsonMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val yamlFactory = YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    private val yamlMapper = ObjectMapper(yamlFactory)

    /** 读取文件为「插入有序」的 key-value；值必须为标量（不可嵌套对象/数组）。 */
    fun read(path: Path, charset: Charset): LinkedHashMap<String, String> {
        val kind = MessageFileKind.fromPath(path)
            ?: error("Unsupported file type: $path")
        return when (kind) {
            MessageFileKind.JSON -> readJson(path, charset)
            MessageFileKind.PROPERTIES -> readProperties(path, charset)
            MessageFileKind.YAML -> readYaml(path, charset)
        }
    }

    /** 用指定格式完整覆盖写入（保留 [LinkedHashMap] 的条目顺序）。 */
    fun write(path: Path, charset: Charset, data: LinkedHashMap<String, String>) {
        val kind = MessageFileKind.fromPath(path)
            ?: error("Unsupported file type: $path")
        when (kind) {
            MessageFileKind.JSON -> writeJson(path, charset, data)
            MessageFileKind.PROPERTIES -> writeProperties(path, charset, data)
            MessageFileKind.YAML -> writeYaml(path, charset, data)
        }
    }

    /**
     * 若 [key] 已存在则移除后追加到映射末尾（等价于「更新并排到最后一项」）；
     * 用于向源文件末尾追加新 key。
     */
    fun appendOrUpdateLast(
        path: Path,
        charset: Charset,
        key: String,
        value: String,
    ) {
        val map = try {
            read(path, charset)
        } catch (_: Exception) {
            LinkedHashMap()
        }
        map.remove(key)
        map[key] = value
        write(path, charset, map)
    }

    /**
     * 在目标文件中放置 [key]=[value]，若不存在该 key，则插在 [anchorKey] 之后；无锚点则末尾。
     */
    fun upsertAfterAnchor(
        path: Path,
        charset: Charset,
        anchorKey: String?,
        key: String,
        value: String,
    ) {
        val map = try {
            read(path, charset)
        } catch (_: Exception) {
            LinkedHashMap()
        }
        map.remove(key)
        if (anchorKey.isNullOrBlank() || !map.containsKey(anchorKey)) {
            map[key] = value
            write(path, charset, map)
            return
        }
        val next = LinkedHashMap<String, String>()
        var inserted = false
        for ((k, v) in map) {
            next[k] = v
            if (!inserted && k == anchorKey) {
                next[key] = value
                inserted = true
            }
        }
        if (!inserted) {
            next[key] = value
        }
        write(path, charset, next)
    }

    private fun readJson(path: Path, charset: Charset): LinkedHashMap<String, String> {
        val root: JsonNode = Files.newBufferedReader(path, charset).use { jsonMapper.readTree(it) }
        if (!root.isObject) error("JSON root must be object")
        val out = LinkedHashMap<String, String>()
        val it = root.fields()
        while (it.hasNext()) {
            val (k, node) = it.next()
            when {
                node.isTextual -> out[k] = node.asText()
                node.isNumber || node.isBoolean -> out[k] = node.asText()
                else -> error("Nested values are not supported for key: $k")
            }
        }
        return out
    }

    private fun writeJson(path: Path, charset: Charset, data: LinkedHashMap<String, String>) {
        Files.newBufferedWriter(path, charset).use { w ->
            jsonMapper.writeValue(w, data)
        }
    }

    private fun readProperties(path: Path, charset: Charset): LinkedHashMap<String, String> {
        val props = Properties()
        Files.newInputStream(path).use { ins ->
            InputStreamReader(ins, charset).use { props.load(it) }
        }
        val out = LinkedHashMap<String, String>()
        for (k in props.stringPropertyNames()) {
            out[k] = props.getProperty(k) ?: ""
        }
        return out
    }

    private fun writeProperties(path: Path, charset: Charset, data: LinkedHashMap<String, String>) {
        val props = Properties()
        data.forEach { (k, v) -> props.setProperty(k, v) }
        Files.newOutputStream(path).use { out ->
            OutputStreamWriter(out, charset).use { w ->
                props.store(w, "")
            }
        }
    }

    private fun readYaml(path: Path, charset: Charset): LinkedHashMap<String, String> {
        val raw: LinkedHashMap<String, Any?> = Files.newBufferedReader(path, charset).use { reader ->
            yamlMapper.readValue(reader, object : TypeReference<LinkedHashMap<String, Any?>>() {})
                ?: LinkedHashMap()
        }
        val out = LinkedHashMap<String, String>()
        for ((key, v) in raw) {
            when (v) {
                is String -> out[key] = v
                is Number, is Boolean -> out[key] = v.toString()
                null -> out[key] = ""
                else -> error("Nested values are not supported for key: $key")
            }
        }
        return out
    }

    private fun writeYaml(path: Path, charset: Charset, data: LinkedHashMap<String, String>) {
        Files.newBufferedWriter(path, charset).use { w ->
            yamlMapper.writerWithDefaultPrettyPrinter().writeValue(w, data)
        }
    }
}
