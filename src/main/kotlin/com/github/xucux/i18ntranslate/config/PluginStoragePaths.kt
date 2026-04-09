package com.github.xucux.i18ntranslate.config

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 配置文件物理路径：
 * - 用户目录下 `i18n_translate_plugin`：全局选项与统计；
 * - 项目 `.idea`：本项目源/目标语言文件路径。
 */
object PluginStoragePaths {
    private const val USER_ROOT = "i18n_translate_plugin"
    private const val GLOBAL_PROPS = "general.properties"
    private const val STATS_PROPS = "stats.properties"
    private const val IDEA_SUB = ".idea"
    private const val PROJECT_PROPS = "i18n-translate-project.properties"

    /** `%USER_HOME%/i18n_translate_plugin`，不存在则创建。 */
    fun userRoot(): Path {
        val dir = Paths.get(System.getProperty("user.home"), USER_ROOT)
        Files.createDirectories(dir)
        return dir
    }

    /** 全局选项（引擎、编码、凭证等）文件路径。 */
    fun globalPropertiesPath(): Path = userRoot().resolve(GLOBAL_PROPS)

    /** 翻译调用统计持久化文件路径。 */
    fun statsPropertiesPath(): Path = userRoot().resolve(STATS_PROPS)

    /** 当前项目 i18n 路径配置的 properties 绝对路径；无项目根时返回 `null`。 */
    fun projectPropertiesPath(project: Project): Path? {
        val base = project.basePath ?: return null
        val idea = Paths.get(base, IDEA_SUB)
        Files.createDirectories(idea)
        return idea.resolve(PROJECT_PROPS)
    }
}
