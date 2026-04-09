package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.ui.TranslationLogDialog
import com.github.xucux.i18ntranslate.ui.TranslateKeyConfirmDialog
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

/** 将当前 key 的 value 译为指定 [target] 并写入对应目标文件。 */
class TranslateKeyToTargetAction(
    private val target: TargetEntry,
) : AnAction() {

    /** 子菜单/工具栏展示名与说明源自 [target]。 */
    init {
        templatePresentation.text = target.language.displayLabelWithCode
        templatePresentation.description = target.filePath
    }

    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 保持文案与当前 [target] 语种一致。 */
    override fun update(e: AnActionEvent) {
        e.presentation.text = target.language.displayLabelWithCode
    }

    /** 确认译文后后台写入单个目标并展示日志。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val keyInfo = I18nKeyDetector.detectKey(editor, editor.document.charsSequence, vf.path) ?: return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val dlg = TranslateKeyConfirmDialog(
            title = I18nTranslateBundle.message("dialog.translate.title", target.language.displayLabelWithCode),
            messageKey = keyInfo.key,
            initialValue = keyInfo.value.orEmpty(),
            targetPath = target.filePath,
        )
        if (!dlg.showAndGet()) return
        val value = dlg.valueField.text
        val ordered = I18nTranslateWorkflow.orderedKeys(project, pstate.sourceFilePath)
        val anchor = I18nKeyDetector.previousKeyInSameFile(ordered, keyInfo.key)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "i18n translate", true) {
            override fun run(indicator: ProgressIndicator) {
                val log = mutableListOf<TranslateLogLine>()
                I18nTranslateWorkflow.translateToTargets(
                    project,
                    value,
                    pstate.sourceLanguage,
                    listOf(target),
                    anchor,
                    keyInfo.key,
                    log,
                )
                ApplicationManager.getApplication().invokeLater {
                    TranslationLogDialog(project, log).show()
                }
            }
        })
    }
}
