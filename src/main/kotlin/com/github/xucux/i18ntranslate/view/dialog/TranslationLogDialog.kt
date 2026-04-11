package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.service.TranslateLogLine
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * 翻译与写文件过程的可滚动日志，支持复制全文。
 *
 * @param lines 自 [com.github.xucux.i18ntranslate.service.I18nTranslateWorkflow] 收集的运行记录
 */
class TranslationLogDialog(
    project: Project?,
    lines: List<TranslateLogLine>,
) : DialogWrapper(project) {

    /** 只读展示全部日志文本。 */
    private val area = JTextArea().apply {
        isEditable = false
        text = lines.joinToString("\n") { it.text }
        rows = 24
        columns = 72
    }

    init {
        title = I18nTranslateBundle.message("log.title")
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        init()
    }

    /** 带滚动条的日志区，预设大致对话框尺寸。 */
    override fun createCenterPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = JBDimension(640, 420)
        panel.add(JBScrollPane(area), BorderLayout.CENTER)
        return panel
    }

    /** 提供「复制」与「确定」按钮。 */
    override fun createActions(): Array<Action> {
        val copyAction = object : DialogWrapperAction(I18nTranslateBundle.message("log.copy")) {
            override fun doAction(e: ActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(area.text))
            }
        }
        return arrayOf(copyAction, myOKAction)
    }

    /** 底部栏由 [createActions] 自行布局，故不显式南面板。 */
    override fun createSouthPanel(): JPanel? = null
}
