package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/** Action 公共逻辑：BGT 更新线程、选中文本、配置校验、源 key 覆盖确认。 */
object I18nActionSupport {
    /** IntelliJ 新 API 要求显式声明在后台线程更新 [AnActionEvent.presentation]。 */
    fun updateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** 编辑器中待作为 properties key 的选区或当前词。 */
    fun selectedKeyText(event: AnActionEvent): String? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        return I18nKeyDetector.selectedTextOrWord(editor)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 未配置源文件路径时提示并返回 false。 */
    fun ensureSourceConfigured(project: Project): Boolean {
        val src = project.getService(ProjectI18nConfigService::class.java).getState().sourceFilePath.trim()
        if (src.isNotBlank()) return true
        Messages.showWarningDialog(
            project,
            I18nTranslateBundle.message("error.no.source"),
            I18nTranslateBundle.message("plugin.name"),
        )
        return false
    }

    /** 没有任何有效目标路径时提示并返回 false。 */
    fun ensureTargetsConfigured(project: Project): Boolean {
        val targets = project.getService(ProjectI18nConfigService::class.java).getState().targets
        if (targets.any { it.filePath.isNotBlank() }) return true
        Messages.showWarningDialog(
            project,
            I18nTranslateBundle.message("error.no.targets"),
            I18nTranslateBundle.message("plugin.name"),
        )
        return false
    }

    /** 源文件已存在同名 key 时，询问是否覆盖（是则返回 true）。 */
    fun confirmSourceKeyOverwrite(project: Project, key: String): Boolean {
        val res = Messages.showYesNoDialog(
            project,
            I18nTranslateBundle.message("confirm.overwrite.source", key),
            I18nTranslateBundle.message("plugin.name"),
            Messages.getQuestionIcon(),
        )
        return res == Messages.YES
    }
}
