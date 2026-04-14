package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.domain.I18nResourceFileNameLanguageGuess
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathDialog
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/** 未配置的 i18n 文件：写入【源文件路径】（需求 3.4，且仅在当前未配置源路径时出现）。 */
class AddSourceFilePathAction : AnAction(
    I18nTranslateBundle.message("action.add.path.source"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    override fun update(e: AnActionEvent) {
        val project = e.project
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val svc = project?.getService(ProjectI18nConfigService::class.java)
        val srcEmpty = svc?.getState()?.sourceFilePath?.isBlank() != false
        e.presentation.isEnabledAndVisible =
            project != null &&
                vf != null &&
                srcEmpty &&
                I18nPaths.isUnboundI18nFile(project, vf)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val svc = project.getService(ProjectI18nConfigService::class.java)
        val storedPath = I18nPaths.pathForStorageFromVirtualFile(vf)
        val guessed = I18nResourceFileNameLanguageGuess.guessFromFileName(vf.name)
        val dlg = AssignI18nFilePathDialog(
            project,
            AssignI18nFilePathMode.SOURCE,
            storedPath,
            sourceLanguageOptions = SupportedLanguage.entries.toList(),
            preferredSourceLanguage = guessed,
        )
        if (!dlg.showAndGet()) return
        val lang = dlg.selectedSourceLanguage() ?: return
        val cur = svc.getState()
        svc.applyState(cur.copy(sourceFilePath = storedPath, sourceLanguage = lang))
    }
}
