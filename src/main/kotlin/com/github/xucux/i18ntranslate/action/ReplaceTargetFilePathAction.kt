package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.ProjectI18nState
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.domain.I18nResourceFileNameLanguageGuess
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathDialog
import com.github.xucux.i18ntranslate.view.dialog.AssignI18nFilePathMode
import com.github.xucux.i18ntranslate.view.dialog.TargetFileBindOption
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * 当前文件已在【目标文件路径】中：重新指定该文件绑定到哪一目标语种（或新增语种行）（需求 3.4）。
 */
class ReplaceTargetFilePathAction : AnAction(
    I18nTranslateBundle.message("action.replace.target.path"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    override fun update(e: AnActionEvent) {
        val project = e.project
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            project != null &&
                vf != null &&
                I18nPaths.isI18nFile(vf) &&
                I18nPaths.isProjectTargetFile(project, vf)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val svc = project.getService(ProjectI18nConfigService::class.java)
        val cur = svc.getState()
        val storedPath = I18nPaths.pathForStorageFromVirtualFile(vf)
        val norm = I18nPaths.normalizePath(storedPath)
        val langFromConfig: SupportedLanguage? = cur.targets.firstOrNull { t ->
            t.filePath.isNotBlank() && I18nPaths.normalizePath(t.filePath) == norm
        }?.language
        val guessed = I18nResourceFileNameLanguageGuess.guessFromFileName(vf.name)
        val preferred = langFromConfig ?: guessed

        val bindOptions = TargetFileBindOption.buildOptions(cur.targets)
        val dlg = AssignI18nFilePathDialog(
            project,
            AssignI18nFilePathMode.TARGET,
            storedPath,
            targetBindOptions = bindOptions,
            preferredTargetLanguage = preferred,
            replaceTargetFlow = true,
        )
        if (!dlg.showAndGet()) return
        val opt = dlg.selectedTargetBindOption() ?: return
        applyReplace(svc, cur, storedPath, norm, opt)
    }

    private fun applyReplace(
        svc: ProjectI18nConfigService,
        cur: ProjectI18nState,
        storedPath: String,
        norm: String,
        option: TargetFileBindOption,
    ) {
        val destLang: SupportedLanguage = when (option) {
            is TargetFileBindOption.UpdateExistingRow -> option.row.language
            is TargetFileBindOption.NewLanguageFromEnum -> option.language
        }
        val newList = cur.targets.map { t ->
            if (I18nPaths.normalizePath(t.filePath) == norm && t.language != destLang) {
                TargetEntry("", t.language)
            } else {
                TargetEntry(t.filePath, t.language)
            }
        }.toMutableList()
        val destIdx = newList.indexOfFirst { it.language == destLang }
        if (destIdx >= 0) {
            newList[destIdx] = TargetEntry(storedPath, destLang)
        } else {
            newList.add(TargetEntry(storedPath, destLang))
        }
        svc.applyState(cur.copy(targets = newList))
    }
}
