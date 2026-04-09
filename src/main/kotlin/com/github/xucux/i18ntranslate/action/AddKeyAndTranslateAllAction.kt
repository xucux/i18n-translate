package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.ui.AddKeyDialog
import com.github.xucux.i18ntranslate.ui.AddKeyDialogMode
import com.github.xucux.i18ntranslate.ui.TranslationLogDialog
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

/** 非资源文件：写入源并翻译到全部目标文件（需求 3.1.2）。 */
class AddKeyAndTranslateAllAction : AnAction() {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 仅在非 i18n 文件且编辑器中有可用选词/当前词时显示。 */
    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val kt = I18nActionSupport.selectedKeyText(e)
        e.presentation.isEnabledAndVisible =
            e.project != null && vf != null && !I18nPaths.isI18nFile(vf) && !kt.isNullOrBlank()
    }

    /** 写入源后按配置对每个目标执行翻译并弹出日志。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!I18nActionSupport.ensureSourceConfigured(project)) return
        if (!I18nActionSupport.ensureTargetsConfigured(project)) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val source = pstate.sourceFilePath
        val keyText = I18nActionSupport.selectedKeyText(e) ?: return
        val preview = pstate.targets.map { it.filePath }
        val dlg = AddKeyDialog(AddKeyDialogMode.ALL_TARGETS, keyText, source, preview)
        if (!dlg.showAndGet()) return
        val key = dlg.keyField.text.trim()
        val value = dlg.valueField.text
        if (key.isEmpty()) {
            Messages.showWarningDialog(project, "Key empty", I18nTranslateBundle.message("plugin.name"))
            return
        }
        if (I18nTranslateWorkflow.sourceContainsKey(project, source, key)) {
            if (!I18nActionSupport.confirmSourceKeyOverwrite(project, key)) return
        }
        val targets = pstate.targets.filter { it.filePath.isNotBlank() }
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
                    targets,
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
