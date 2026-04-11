package com.github.xucux.i18ntranslate.service

import com.github.xucux.i18ntranslate.config.FileWriteMode
import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.github.xucux.i18ntranslate.config.I18nStatsService
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.file.FlatMessageFileIo
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.translation.engine.TranslationExecutors
import com.github.xucux.i18ntranslate.util.CharsetUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/** 执行日志对话框中的一行可读文本。 */
data class TranslateLogLine(
    /** 展示给用户的发展过程说明（含前缀空格表示层级）。 */
    val text: String,
)

/**
 * 跨写动作与翻译编排：读全局/项目配置、写源与目标资源文件、调用 [TranslationExecutors]。
 */
object I18nTranslateWorkflow {

    /** 在源资源文件中追加或更新 [key]，保证其出现在映射顺序末尾（见 [FlatMessageFileIo.appendOrUpdateLast]）。 */
    fun appendSourceKey(
        project: Project,
        sourcePathStr: String,
        key: String,
        value: String,
    ) {
        val global = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java)
        val charset = CharsetUtil.resolve(global.getState().resolveCharset())
        val path = Paths.get(sourcePathStr)
        WriteAction.runAndWait<Throwable> {
            FlatMessageFileIo.appendOrUpdateLast(path, charset, key, value)
        }
    }

    /** 源文件中是否已有该 key（用于覆盖确认）。 */
    fun sourceContainsKey(project: Project, sourcePathStr: String, key: String): Boolean {
        val global = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java)
        val charset = CharsetUtil.resolve(global.getState().resolveCharset())
        val path = Paths.get(sourcePathStr)
        return runCatching {
            FlatMessageFileIo.read(path, charset).containsKey(key)
        }.getOrDefault(false)
    }

    /** 读取源文件中某 key 的 value。 */
    fun readSourceValue(project: Project, sourcePathStr: String, key: String): String? {
        val global = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java)
        val charset = CharsetUtil.resolve(global.getState().resolveCharset())
        val path = Paths.get(sourcePathStr)
        return runCatching { FlatMessageFileIo.read(path, charset)[key] }.getOrNull()
    }

    /** 源文件中 key 的顺序列表（与文件内出现顺序一致）。 */
    fun orderedKeys(project: Project, sourcePathStr: String): List<String> {
        val global = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java)
        val charset = CharsetUtil.resolve(global.getState().resolveCharset())
        val path = Paths.get(sourcePathStr)
        return runCatching { FlatMessageFileIo.read(path, charset).keys.toList() }.getOrDefault(emptyList())
    }

    /**
     * 将 [textToTranslate] 译为多个 [targets] 语种，并写入对应文件。
     * [anchorKey] 为源文件中上一 key，用于在目标文件中保持相对插入位置；
     * 全局为 [FileWriteMode.SKIP] 时若目标已存在该 key 则跳过。
     */
    fun translateToTargets(
        project: Project,
        textToTranslate: String,
        sourceLang: SupportedLanguage,
        targets: List<TargetEntry>,
        anchorKey: String?,
        key: String,
        log: MutableList<TranslateLogLine>,
    ) {
        val app = ApplicationManager.getApplication()
        val global = app.getService(GlobalPluginConfigService::class.java)
        val stats = app.getService(I18nStatsService::class.java)
        val state = global.getState()
        val charset = CharsetUtil.resolve(state.resolveCharset())
        val executor = TranslationExecutors.create(state)
        val writeMode = state.writeMode

        for (t in targets) {
            val path = Paths.get(t.filePath)
            log.add(TranslateLogLine("→ ${t.language.displayLabelWithCode} (${t.filePath})"))
            if (writeMode == FileWriteMode.SKIP) {
                val exists = runCatching { FlatMessageFileIo.read(path, charset).containsKey(key) }.getOrDefault(false)
                if (exists) {
                    log.add(TranslateLogLine("  SKIP (key exists)"))
                    continue
                }
            }
            val outcome = TranslationExecutors.translateWithStats(
                executor,
                stats,
                textToTranslate,
                sourceLang,
                t.language,
            )
            if (!outcome.ok) {
                log.add(TranslateLogLine("  FAIL: ${outcome.errorMessage}"))
                continue
            }
            WriteAction.runAndWait<Throwable> {
                FlatMessageFileIo.upsertAfterAnchor(path, charset, anchorKey, key, outcome.text!!)
            }
            log.add(TranslateLogLine("  OK: ${outcome.text}"))
        }
    }

    /** 按源文件 key 顺序依次翻译；锚点为同文件中的上一个 key。 */
    fun translateKeysSequential(
        project: Project,
        sourcePathStr: String,
        sourceLang: SupportedLanguage,
        keys: List<String>,
        targets: List<TargetEntry>,
        log: MutableList<TranslateLogLine>,
    ) {
        keys.forEachIndexed { index, key ->
            val anchor = if (index > 0) keys[index - 1] else null
            val value = readSourceValue(project, sourcePathStr, key).orEmpty()
            log.add(TranslateLogLine("— ${key} = ${value.take(80)}${if (value.length > 80) "…" else ""}"))
            translateToTargets(project, value, sourceLang, targets, anchor, key, log)
        }
    }
}
