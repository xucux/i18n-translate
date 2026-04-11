package com.github.xucux.i18ntranslate.view.dialog

import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 垂直堆叠、每行「标签 | 控件」；控件在水平方向随对话框拉伸（类流式表单的响应式宽度）。
 */
internal fun labeledGridFormPanel(rows: List<LabeledFormRow>): JPanel {
    val root = JPanel(GridBagLayout())
    val labelC = GridBagConstraints().apply {
        gridx = 0
        anchor = GridBagConstraints.NORTHWEST
        fill = GridBagConstraints.NONE
        weightx = 0.0
        weighty = 0.0
        insets = JBUI.insets(2, 0, 6, 8)
    }
    val fieldC = GridBagConstraints().apply {
        gridx = 1
        anchor = GridBagConstraints.NORTHWEST
        insets = JBUI.insets(2, 0, 6, 0)
    }
    rows.forEachIndexed { index, row ->
        val isLast = index == rows.lastIndex
        labelC.gridy = index
        root.add(JLabel(row.labelText), labelC)
        fieldC.gridy = index
        if (row.stretchVertically) {
            fieldC.fill = GridBagConstraints.BOTH
            fieldC.weightx = 1.0
            fieldC.weighty = if (isLast) 1.0 else 0.0
        } else {
            fieldC.fill = GridBagConstraints.HORIZONTAL
            fieldC.weightx = 1.0
            fieldC.weighty = 0.0
        }
        root.add(row.component, fieldC)
    }
    return root
}

internal data class LabeledFormRow(
    val labelText: String,
    val component: JComponent,
    /** 为 true 时（如带滚动条的多行区）占用对话框纵向剩余空间。 */
    val stretchVertically: Boolean = false,
)
