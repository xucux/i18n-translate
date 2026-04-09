package com.github.xucux.i18ntranslate.util

import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/**
 * 路径与文件类型辅助：与配置中的源文件路径比较、判断是否资源扩展名。
 */
object I18nPaths {
    /** 规范化路径，便于跨 Windows/Unix 与配置项做字符串比较。 */
    fun normalizePath(path: String): String =
        runCatching { Paths.get(path.trim()).normalize().toString().replace('\\', '/') }
            .getOrDefault(path.trim())

    /** 是否为插件支持的单层消息文件类型（由扩展名判断）。 */
    fun isI18nFile(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in setOf("properties", "json", "yaml", "yml")
    }

    /** 当前 [file] 是否等于项目设置里配置的「源语言」资源文件路径。 */
    fun isProjectSourceFile(project: Project, file: VirtualFile): Boolean {
        val src = project.getService(ProjectI18nConfigService::class.java).getState().sourceFilePath.trim()
        if (src.isBlank()) return false
        return normalizePath(src) == normalizePath(file.path)
    }
}
