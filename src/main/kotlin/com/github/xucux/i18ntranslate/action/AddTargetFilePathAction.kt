package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.domain.I18nResourceFileNameLanguageGuess
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathDialog
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathMode
import com.github.xucux.i18ntranslate.view.dialog.TargetFileBindOption
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

/** 未纳入目标的 i18n 文件：绑定到目标配置（需求 3.4）。语种来自枚举；新建目标时排除已在目标中出现的语种。 */
class AddTargetFilePathAction : AnAction(
    I18nTranslateBundle.message("action.add.path.target"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    override fun update(e: AnActionEvent) {
        val project = e.project
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            project != null && vf != null && I18nPaths.isUnboundI18nFile(project, vf)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val storedPath = I18nPaths.pathForStorageFromVirtualFile(vf)
        val svc = project.getService(ProjectI18nConfigService::class.java)
        val cur = svc.getState()
        val bindOptions = TargetFileBindOption.buildOptions(cur.targets)
        val guessed = I18nResourceFileNameLanguageGuess.guessFromFileName(vf.name)
        val dlg = AssignI18nFilePathDialog(
            project,
            AssignI18nFilePathMode.TARGET,
            storedPath,
            targetBindOptions = bindOptions,
            preferredTargetLanguage = guessed,
        )
        if (!dlg.showAndGet()) return
        val opt = dlg.selectedTargetBindOption() ?: return
        when (opt) {
            is TargetFileBindOption.UpdateExistingRow -> {
                val pick = opt.row
                val pickPath = I18nPaths.normalizePath(pick.filePath)
                val idx = cur.targets.indexOfFirst {
                    it.language == pick.language && I18nPaths.normalizePath(it.filePath) == pickPath
                }
                if (idx < 0) {
                    Messages.showErrorDialog(
                        project,
                        I18nTranslateBundle.message("error.assign.target.not.found"),
                        I18nTranslateBundle.message("plugin.name"),
                    )
                    return
                }
                val newTargets = cur.targets.mapIndexed { i, t ->
                    if (i == idx) TargetEntry(storedPath, t.language) else TargetEntry(t.filePath, t.language)
                }.toMutableList()
                svc.applyState(cur.copy(targets = newTargets))
            }
            is TargetFileBindOption.NewLanguageFromEnum -> {
                val next = cur.targets.toMutableList()
                next.add(TargetEntry(storedPath, opt.language))
                svc.applyState(cur.copy(targets = next))
            }
        }
    }
}
