package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.view.dialog.AddKeyDialog
import com.github.xucux.i18ntranslate.view.dialog.AddKeyDialogMode
import com.github.xucux.i18ntranslate.view.dialog.TranslationLogDialog
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

/** 非资源文件：写入源并仅翻译到用户选定的一个目标（需求 3.1.3）。 */
class AddKeyAndTranslateOneAction : AnAction() {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 仅在非 i18n 文件且编辑器中有可用选词/当前词时显示。 */
    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val kt = I18nActionSupport.selectedKeyText(e)
        e.presentation.isEnabledAndVisible =
            e.project != null && vf != null && !I18nPaths.isI18nFile(vf) && !kt.isNullOrBlank()
    }

    /** 写入源后由用户选一个目标并仅译写该目标，最后弹出日志。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!I18nActionSupport.ensureSourceConfigured(project)) return
        if (!I18nActionSupport.ensureTargetsConfigured(project)) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val source = pstate.sourceFilePath
        val options = pstate.targets.filter { it.filePath.isNotBlank() }
        if (options.isEmpty()) {
            Messages.showWarningDialog(project, I18nTranslateBundle.message("error.no.targets"), I18nTranslateBundle.message("plugin.name"))
            return
        }
        val keyText = I18nActionSupport.selectedKeyText(e) ?: return
        val dlg = AddKeyDialog(
            AddKeyDialogMode.SINGLE_TARGET,
            keyText,
            source,
            targetOptions = options,
        )
        if (!dlg.showAndGet()) return
        val target = dlg.selectedTarget() ?: return
        val key = dlg.keyField.text.trim()
        val value = dlg.valueField.text
        if (key.isEmpty()) {
            Messages.showWarningDialog(project, "Key empty", I18nTranslateBundle.message("plugin.name"))
            return
        }
        if (I18nTranslateWorkflow.sourceContainsKey(project, source, key)) {
            if (!I18nActionSupport.confirmSourceKeyOverwrite(project, key)) return
        }
        val sourceLang = pstate.sourceLanguage
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "i18n translate", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                I18nTranslateWorkflow.appendSourceKey(project, source, key, value)
                val log = mutableListOf<TranslateLogLine>()
                val ordered = I18nTranslateWorkflow.orderedKeys(project, source)
                val anchor = if (ordered.size >= 2) ordered[ordered.size - 2] else null
                I18nTranslateWorkflow.translateToTargets(
                    project,
                    value,
                    sourceLang,
                    listOf(target),
                    anchor,
                    key,
                    log,
                )
                ApplicationManager.getApplication().invokeLater {
                    TranslationLogDialog(project, log).show()
                }
            }
        })
    }
}
