package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
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
import com.github.xucux.i18ntranslate.util.I18nPaths

/** 将选区内多个 key 译为所有已配置目标并写入对应文件。 */
class TranslateSelectedKeysToAllTargetsAction : AnAction(
    I18nTranslateBundle.message("action.translate.selected.all"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

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
        if (!editor.selectionModel.hasSelection()) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val keys = I18nKeyDetector.selectedKeys(editor, vf.path)
        val hasTargets = project.getService(ProjectI18nConfigService::class.java).getState().targets.any { it.filePath.isNotBlank() }
        e.presentation.isEnabledAndVisible = keys.isNotEmpty() && hasTargets
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!I18nActionSupport.ensureTargetsConfigured(project)) return
        val entries = I18nKeyDetector.selectedKeyValues(editor, vf.path)
        if (entries.isEmpty()) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val targets = pstate.targets.filter { it.filePath.isNotBlank() }
        val dlg = TranslateSelectedKeysDialog(
            title = I18nTranslateBundle.message("dialog.translate.selected.all.title"),
            entries = entries,
            targetPaths = targets.map { "${it.language.displayLabelWithCode}: ${it.filePath}" },
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
                        targets,
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
