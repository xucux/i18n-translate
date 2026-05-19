package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator

/**
 * 编辑器 / 项目视图统一入口：根据上下文展示 3.1 / 3.2 / 3.3 相关子项。
 */
class I18nEditorPopupGroup : ActionGroup(
    I18nTranslateBundle.message("action.group"),
    true,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = I18nActionSupport.updateThread()

    /** 依上下文拼装「添加 key / 译到目标 / 整文件」等子项与分隔线。 */
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return AnAction.EMPTY_ARRAY
        val project = e.project ?: return AnAction.EMPTY_ARRAY
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val am = ActionManager.getInstance()
        fun action(id: String): AnAction = checkNotNull(am.getAction(id)) { "Missing action $id" }
        val out = mutableListOf<AnAction>()

        if (vf != null && editor != null && !I18nPaths.isI18nFile(vf)) {
            val sel = I18nActionSupport.selectedKeyText(e)
            if (!sel.isNullOrBlank()) {
                out.add(action("I18nTranslate.AddSource"))
                out.add(action("I18nTranslate.AddTranslateAll"))
                out.add(action("I18nTranslate.AddTranslateOne"))
            }
        }

        if (vf != null && I18nPaths.isUnboundI18nFile(project, vf)) {
            if (out.isNotEmpty()) out.add(Separator.create())
            val cfg = project.getService(ProjectI18nConfigService::class.java).getState()
            if (cfg.sourceFilePath.isBlank()) {
                out.add(action("I18nTranslate.AddSourceFilePath"))
            }
            out.add(action("I18nTranslate.AddTargetFilePath"))
        }

        if (vf != null && I18nPaths.isI18nFile(vf) && I18nPaths.isProjectTargetFile(project, vf)) {
            if (out.isNotEmpty()) out.add(Separator.create())
            out.add(action("I18nTranslate.ReplaceTargetFilePath"))
        }

        if (vf != null && I18nPaths.isI18nFile(vf) && I18nPaths.isProjectSourceFile(project, vf)) {
            if (editor != null) {
                val hasTargets = project.getService(ProjectI18nConfigService::class.java).getState().targets
                    .any { it.filePath.isNotBlank() }
                val keyInfo = I18nKeyDetector.detectKey(editor, editor.document.charsSequence, vf.path)
                if (keyInfo != null && hasTargets) {
                    if (out.isNotEmpty()) out.add(Separator.create())
                    out.add(am.getAction("I18nTranslate.TranslateToTargetGroup")!!)
                    out.add(action("I18nTranslate.KeyToAllTargets"))
                }

                val selectedKeys = I18nKeyDetector.selectedKeys(editor, vf.path)
                if (selectedKeys.isNotEmpty() && hasTargets) {
                    if (out.isNotEmpty()) out.add(Separator.create())
                    out.add(am.getAction("I18nTranslate.SelectedKeysToTargetGroup")!!)
                    out.add(action("I18nTranslate.SelectedKeysToAllTargets"))
                }
            }
            if (out.isNotEmpty()) out.add(Separator.create())
            out.add(action("I18nTranslate.FileAllKeys"))
        }

        return out.toTypedArray()
    }

    /** 无可用子项时隐藏本菜单组。 */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = hasVisibleChildren(e)
    }

    /**
     * 仅用于 update 阶段快速判断是否可能有可见子项，避免在插件侧直接调用 OverrideOnly 的 getChildren。
     */
    private fun hasVisibleChildren(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        val editor = e.getData(CommonDataKeys.EDITOR)

        if (editor != null && !I18nPaths.isI18nFile(vf)) {
            val sel = I18nActionSupport.selectedKeyText(e)
            if (!sel.isNullOrBlank()) return true
        }

        if (I18nPaths.isUnboundI18nFile(project, vf)) return true

        if (I18nPaths.isI18nFile(vf) && I18nPaths.isProjectTargetFile(project, vf)) return true

        if (!I18nPaths.isI18nFile(vf) || !I18nPaths.isProjectSourceFile(project, vf)) return false

        if (editor != null) {
            val hasTargets = project.getService(ProjectI18nConfigService::class.java).getState().targets
                .any { it.filePath.isNotBlank() }
            val keyInfo = I18nKeyDetector.detectKey(editor, editor.document.charsSequence, vf.path)
            if (keyInfo != null && hasTargets) return true
            if (editor.selectionModel.hasSelection()) {
                val selKeys = I18nKeyDetector.selectedKeys(editor, vf.path)
                if (selKeys.isNotEmpty() && hasTargets) return true
            }
        }

        return true
    }

}
