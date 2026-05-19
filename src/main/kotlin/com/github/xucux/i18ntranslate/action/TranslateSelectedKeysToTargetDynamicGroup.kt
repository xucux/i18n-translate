package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** 选区多个 key：按配置动态生成「译到各目标语种」子菜单。 */
class TranslateSelectedKeysToTargetDynamicGroup : ActionGroup(
    I18nTranslateBundle.message("action.translate.selected.to"),
    true,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = I18nActionSupport.updateThread()

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val p = e?.project ?: return AnAction.EMPTY_ARRAY
        val targets = p.getService(ProjectI18nConfigService::class.java).getState().targets
            .filter { it.filePath.isNotBlank() }
        return targets.map { TranslateSelectedKeysToTargetAction(it) }.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val p = e.project ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val n = p.getService(ProjectI18nConfigService::class.java).getState().targets.count { it.filePath.isNotBlank() }
        e.presentation.isEnabledAndVisible = n > 0
    }
}
