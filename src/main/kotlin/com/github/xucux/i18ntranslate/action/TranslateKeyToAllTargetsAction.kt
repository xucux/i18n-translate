package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.ui.TranslateKeyConfirmDialog
import com.github.xucux.i18ntranslate.ui.TranslationLogDialog
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

/** 资源文件 key：一次确认后写入所有已配置目标（需求 3.2.1 全部目标）。 */
class TranslateKeyToAllTargetsAction : AnAction(
    I18nTranslateBundle.message("action.translate.all.targets"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 在已配置的源资源文件内且能解析出当前行 key、且存在有效目标时可用。 */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || editor == null || vf == null ||
            !I18nPaths.isI18nFile(vf) || !I18nPaths.isProjectSourceFile(project, vf)
        ) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val keyInfo = I18nKeyDetector.detectKey(editor, editor.document.charsSequence, vf.path)
        val hasTargets = project.getService(ProjectI18nConfigService::class.java).getState().targets.any { it.filePath.isNotBlank() }
        e.presentation.isEnabledAndVisible = keyInfo != null && hasTargets
    }

    /** 一次确认后向所有已配置目标写回翻译并展示日志。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val keyInfo = I18nKeyDetector.detectKey(editor, editor.document.charsSequence, vf.path) ?: return
        if (!I18nActionSupport.ensureTargetsConfigured(project)) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val targets = pstate.targets.filter { it.filePath.isNotBlank() }
        val dlg = TranslateKeyConfirmDialog(
            title = I18nTranslateBundle.message("dialog.translate.all.title"),
            messageKey = keyInfo.key,
            initialValue = keyInfo.value.orEmpty(),
            targetPath = targets.firstOrNull()?.filePath.orEmpty(),
            extraSourcePath = pstate.sourceFilePath,
            extraTargetPaths = targets.map { "${it.language.displayLabelWithCode}: ${it.filePath}" },
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
                    targets,
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
