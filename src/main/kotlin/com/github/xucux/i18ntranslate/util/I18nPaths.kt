package com.github.xucux.i18ntranslate.util

import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/**
 * 路径与文件类型辅助：与配置中的源文件路径比较、判断是否资源扩展名。
 */
object I18nPaths {

    /**
     * 右键等方式写入【国际化配置】的磁盘路径：使用当前 OS 惯用分隔符（Windows 为 `\`）。
     * [VirtualFile.getPath] 在 IDE 中多为 `/`，此处转为与 [java.nio.file.Path] 一致的本机字符串。
     */
    fun pathForStorageFromVirtualFile(file: VirtualFile): String =
        pathForStorage(file.path)

    /**
     * 将路径规范为当前 OS 下用于持久化的形式（与 [pathForStorageFromVirtualFile] 一致）。
     */
    fun pathForStorage(rawPathFromIde: String): String =
        runCatching {
            val cleaned = rawPathFromIde.trim().replace('\\', '/')
            Paths.get(cleaned).toAbsolutePath().normalize().toString()
        }.getOrElse { rawPathFromIde.trim() }

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

    /** 当前 [file] 是否与某一已配置目标消息文件路径相同。 */
    fun isProjectTargetFile(project: Project, file: VirtualFile): Boolean {
        val norm = normalizePath(file.path)
        return project.getService(ProjectI18nConfigService::class.java).getState().targets
            .any { it.filePath.isNotBlank() && normalizePath(it.filePath) == norm }
    }

    /**
     * 支持扩展名的消息文件，且既不是当前配置的源文件也不是任一目标文件（需求 3.4）。
     */
    fun isUnboundI18nFile(project: Project, file: VirtualFile): Boolean =
        isI18nFile(file) && !isProjectSourceFile(project, file) && !isProjectTargetFile(project, file)
}
