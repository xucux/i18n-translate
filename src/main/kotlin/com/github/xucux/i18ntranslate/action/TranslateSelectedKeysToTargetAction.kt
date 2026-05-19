package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.view.dialog.TranslateSelectedKeysDialog
import com.github.xucux.i18ntranslate.view.dialog.TranslationLogDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

/** 将选区内多个 key 译为指定 [target] 并写入对应目标文件。 */
class TranslateSelectedKeysToTargetAction(
    private val target: TargetEntry,
) : AnAction() {

    init {
        templatePresentation.text = target.language.displayLabelWithCode
        templatePresentation.description = target.filePath
    }

    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    override fun update(e: AnActionEvent) {
        e.presentation.text = target.language.displayLabelWithCode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val entries = I18nKeyDetector.selectedKeyValues(editor, vf.path)
        if (entries.isEmpty()) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val dlg = TranslateSelectedKeysDialog(
            title = I18nTranslateBundle.message("dialog.translate.title", target.language.displayLabelWithCode),
            entries = entries,
            targetPaths = listOf(target.filePath),
            singleTarget = true,
        )
        if (!dlg.showAndGet()) return
        val confirmed = dlg.confirmedEntries()
        val ordered = I18nTranslateWorkflow.orderedKeys(project, pstate.sourceFilePath)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "i18n translate", true) {
            override fun run(indicator: ProgressIndicator) {
                val log = mutableListOf<TranslateLogLine>()
                for (entry in confirmed) {
                    val anchor = I18nKeyDetector.previousKeyInSameFile(ordered, entry.key)
                    log.add(TranslateLogLine("— ${entry.key} = ${entry.value.take(80)}${if (entry.value.length > 80) "…" else ""}"))
                    I18nTranslateWorkflow.translateToTargets(
                        project,
                        entry.value,
                        pstate.sourceLanguage,
                        listOf(target),
                        anchor,
                        entry.key,
                        log,
                    )
                }
                ApplicationManager.getApplication().invokeLater {
                    TranslationLogDialog(project, log).show()
                }
            }
        })
    }
}
