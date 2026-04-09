package com.github.xucux.i18ntranslate.config

import com.github.xucux.i18ntranslate.lang.SupportedLanguage
import com.github.xucux.i18ntranslate.config.PluginStoragePaths.projectPropertiesPath
import com.intellij.openapi.project.Project
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

/** 单个目标语言及其对应的消息文件磁盘路径。 */
data class TargetEntry(
    /** 目标 messages 文件路径。 */
    var filePath: String,
    /** 该文件所表示的自然语言。 */
    var language: SupportedLanguage,
)

/**
 * 项目级 i18n 路径配置：源文件、源语种、多目标。
 * 序列化至 [PluginStoragePaths.projectPropertiesPath]。
 */
data class ProjectI18nState(
    /** 项目源语言消息文件路径。 */
    var sourceFilePath: String = "",
    /** 源文件内容所使用自然语言。 */
    var sourceLanguage: SupportedLanguage = SupportedLanguage.CHINESE,
    /** 需写入翻译结果的多个目标项（路径 + 语种）。 */
    var targets: MutableList<TargetEntry> = mutableListOf(),
) {
    companion object {
        private const val K_SOURCE_PATH = "source.path"
        private const val K_SOURCE_LANG = "source.lang"
        private const val K_TARGET_COUNT = "target.count"

        /** 从当前项目 `.idea` 下 properties 加载。 */
        fun load(project: Project): ProjectI18nState {
            val path = projectPropertiesPath(project) ?: return ProjectI18nState()
            if (!Files.isRegularFile(path)) return ProjectI18nState()
            val props = Properties()
            Files.newInputStream(path).use { ins ->
                InputStreamReader(ins, StandardCharsets.UTF_8).use { props.load(it) }
            }
            val srcLang = props.getProperty(K_SOURCE_LANG)?.let { n ->
                runCatching { SupportedLanguage.valueOf(n) }.getOrNull()
            } ?: SupportedLanguage.CHINESE
            val count = props.getProperty(K_TARGET_COUNT, "0").toIntOrNull() ?: 0
            val targets = mutableListOf<TargetEntry>()
            repeat(count) { i ->
                val p = props.getProperty("target.$i.path") ?: return@repeat
                val langName = props.getProperty("target.$i.lang") ?: return@repeat
                val lang = runCatching { SupportedLanguage.valueOf(langName) }.getOrNull() ?: return@repeat
                targets.add(TargetEntry(p, lang))
            }
            return ProjectI18nState(
                sourceFilePath = props.getProperty(K_SOURCE_PATH, ""),
                sourceLanguage = srcLang,
                targets = targets,
            )
        }

        /** 将 [state] 写入项目目录（UTF-8）。 */
        fun save(project: Project, state: ProjectI18nState) {
            val path = projectPropertiesPath(project) ?: return
            val props = Properties()
            props.setProperty(K_SOURCE_PATH, state.sourceFilePath)
            props.setProperty(K_SOURCE_LANG, state.sourceLanguage.name)
            props.setProperty(K_TARGET_COUNT, state.targets.size.toString())
            state.targets.forEachIndexed { i, t ->
                props.setProperty("target.$i.path", t.filePath)
                props.setProperty("target.$i.lang", t.language.name)
            }
            Files.createDirectories(path.parent)
            Files.newOutputStream(path).use { out ->
                OutputStreamWriter(out, StandardCharsets.UTF_8).use { w ->
                    props.store(w, "I18N Translate — project")
                }
            }
        }
    }
}
