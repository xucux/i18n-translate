package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.view.dialog.AddKeyDialog
import com.github.xucux.i18ntranslate.view.dialog.AddKeyDialogMode
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

/** 非资源文件：仅将 key/value 写入配置的源资源文件（需求 3.1.1）。 */
class AddKeyToSourceAction : AnAction() {

    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 仅在非 i18n 文件且编辑器中有可用选词/当前词时显示。 */
    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val kt = I18nActionSupport.selectedKeyText(e)
        e.presentation.isEnabledAndVisible =
            e.project != null && vf != null && !I18nPaths.isI18nFile(vf) && !kt.isNullOrBlank()
    }

    /** 弹窗录入 key/value，必要时确认覆盖，再在后台仅写入源文件。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!I18nActionSupport.ensureSourceConfigured(project)) return
        val source = project.getService(ProjectI18nConfigService::class.java).getState().sourceFilePath
        val keyText = I18nActionSupport.selectedKeyText(e) ?: return
        val dlg = AddKeyDialog(AddKeyDialogMode.SOURCE_ONLY, keyText, source)
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
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "i18n write", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                I18nTranslateWorkflow.appendSourceKey(project, source, key, value)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        project,
                        "OK",
                        I18nTranslateBundle.message("plugin.name"),
                    )
                }
            }
        })
    }
}
