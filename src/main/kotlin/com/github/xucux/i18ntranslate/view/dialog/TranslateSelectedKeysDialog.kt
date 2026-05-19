package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.editor.I18nKeyDetector
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.DialogWrapper
import org.jdesktop.swingx.JXMultiSplitPane
import org.jdesktop.swingx.MultiSplitLayout
import org.jdesktop.swingx.MultiSplitLayout.Divider
import org.jdesktop.swingx.MultiSplitLayout.Leaf
import org.jdesktop.swingx.MultiSplitLayout.Node
import org.jdesktop.swingx.MultiSplitLayout.Split
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

/** 批量选中 keys 翻译前的确认弹窗：左为 key-value 列表，右为目标路径。 */
class TranslateSelectedKeysDialog(
    title: String,
    private val entries: List<I18nKeyDetector.SelectedKeyValue>,
    targetPaths: List<String>,
    private val singleTarget: Boolean = false,
) : DialogWrapper(null) {

    private val keyValueTable = JBTable(
        object : DefaultTableModel(
            entries.map { arrayOf(it.key, it.value) }.toTypedArray(),
            arrayOf(
                I18nTranslateBundle.message("dialog.properties.key"),
                I18nTranslateBundle.message("dialog.properties.val"),
            ),
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        },
    ).apply {
        preferredScrollableViewportSize = Dimension(JBUI.scale(360), JBUI.scale(200))
        setDragEnabled(false)
        getColumnModel().getColumn(0).preferredWidth = JBUI.scale(140)
        getColumnModel().getColumn(1).preferredWidth = JBUI.scale(200)
    }

    private val targetArea = JBTextArea(targetPaths.joinToString("\n")).apply {
        isEditable = false
        rows = 6
    }

    init {
        this.title = title
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        isResizable = true
        init()
    }

    /** 用户确认后返回选中的 key-value 列表。 */
    fun confirmedEntries(): List<I18nKeyDetector.SelectedKeyValue> = entries

    override fun createCenterPanel(): JComponent {
        val leftScroll = JBScrollPane(keyValueTable).apply {
            preferredSize = Dimension(JBUI.scale(380), JBUI.scale(240))
        }
        val targetLabel = javax.swing.JLabel(I18nTranslateBundle.message(
            if (singleTarget) "settings.target.path" else "dialog.target.paths",
        ))
        val rightPanel = javax.swing.JPanel(java.awt.BorderLayout()).apply {
            add(targetLabel, java.awt.BorderLayout.NORTH)
            add(JBScrollPane(targetArea), java.awt.BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(300), JBUI.scale(240))
        }
        val leftLeaf = Leaf("left")
        val rightLeaf = Leaf("right")
        val root = Split().apply {
            isRowLayout = true
            children = listOf<Node>(leftLeaf, Divider(), rightLeaf)
        }
        val layout = MultiSplitLayout().apply {
            model = root
            dividerSize = JBUI.scale(6)
        }
        leftLeaf.weight = 0.55
        return JXMultiSplitPane(layout).apply {
            add(leftScroll, "left")
            add(rightPanel, "right")
        }
    }
}
