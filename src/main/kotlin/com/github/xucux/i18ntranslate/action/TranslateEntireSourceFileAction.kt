package com.github.xucux.i18ntranslate.action

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.github.xucux.i18ntranslate.view.dialog.TranslateFileKeysDialog
import com.github.xucux.i18ntranslate.view.dialog.TranslationLogDialog
import com.github.xucux.i18ntranslate.util.CharsetUtil
import com.github.xucux.i18ntranslate.util.I18nPaths
import com.github.xucux.i18ntranslate.file.FlatMessageFileIo
import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.nio.file.Paths

/** 源资源文件右键：按 key 顺序批量翻译到选定的一个目标文件（需求 3.3）。 */
class TranslateEntireSourceFileAction : AnAction(
    I18nTranslateBundle.message("action.file.translate.all"),
    null,
    null,
) {
    override fun getActionUpdateThread() = I18nActionSupport.updateThread()

    /** 仅在当前文件为项目配置的源 i18n 资源时显示。 */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            project != null && vf != null && I18nPaths.isI18nFile(vf!!) && I18nPaths.isProjectSourceFile(project!!, vf)
    }

    /** 选择单个目标后按源 key 顺序逐条翻译写入并展示汇总日志。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!I18nActionSupport.ensureTargetsConfigured(project)) return
        val pstate = project.getService(ProjectI18nConfigService::class.java).getState()
        val source = pstate.sourceFilePath
        val options = pstate.targets.filter { it.filePath.isNotBlank() }
        val global = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java).getState()
        val charset = CharsetUtil.resolve(global.resolveCharset())
        val keyCount = runCatching {
            FlatMessageFileIo.read(Paths.get(source), charset).size
        }.getOrDefault(0)
        val dlg = TranslateFileKeysDialog(keyCount, source, options)
        if (!dlg.showAndGet()) return
        val target = dlg.selectedTarget() ?: return
        val keys = I18nTranslateWorkflow.orderedKeys(project, source)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "i18n file translate", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val log = mutableListOf<TranslateLogLine>()
                var i = 0
                for (k in keys) {
                    indicator.fraction = if (keys.isEmpty()) 1.0 else i.toDouble() / keys.size
                    indicator.text = k
                    val v = I18nTranslateWorkflow.readSourceValue(project, source, k).orEmpty()
                    log.add(TranslateLogLine("— $k"))
                    val anchor = I18nKeyDetector.previousKeyInSameFile(keys, k)
                    I18nTranslateWorkflow.translateToTargets(
                        project,
                        v,
                        pstate.sourceLanguage,
                        listOf(target),
                        anchor,
                        k,
                        log,
                    )
                    i++
                }
                ApplicationManager.getApplication().invokeLater {
                    TranslationLogDialog(project, log).show()
                }
            }
        })
    }
}
