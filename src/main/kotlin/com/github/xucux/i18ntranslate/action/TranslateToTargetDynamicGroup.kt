package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** 资源文件中某一 key：按配置动态生成「译到各目标语种」子菜单（需求 3.2）。 */
class TranslateToTargetDynamicGroup : ActionGroup(
    I18nTranslateBundle.message("action.translate.to"),
    true,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = I18nActionSupport.updateThread()

    /** 每个有效目标生成一个 [TranslateKeyToTargetAction]。 */
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val p = e?.project ?: return AnAction.EMPTY_ARRAY
        val targets = p.getService(ProjectI18nConfigService::class.java).getState().targets
            .filter { it.filePath.isNotBlank() }
        return targets.map { TranslateKeyToTargetAction(it) }.toTypedArray()
    }

    /** 至少存在一个有效目标路径时显示该组。 */
    override fun update(e: AnActionEvent) {
        val p = e.project ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val n = p.getService(ProjectI18nConfigService::class.java).getState().targets.count { it.filePath.isNotBlank() }
        e.presentation.isEnabledAndVisible = n > 0
    }
}
