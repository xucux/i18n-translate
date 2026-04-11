package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField

/** 添加 key 弹窗的三种展示：仅源 / 展示全部目标路径 / 下拉选一个目标。 */
enum class AddKeyDialogMode {
    /** 仅写入源文件，不展示目标选择。 */
    SOURCE_ONLY,
    /** 以只读多行文本列出全部目标路径。 */
    ALL_TARGETS,
    /** 以下拉框选择一个 [TargetEntry]。 */
    SINGLE_TARGET,
}

/**
 * 在源码中选词后录入 key、value，必要时选择或预览目标文件（见 [AddKeyDialogMode]）。
 *
 * @param targetPathsPreview [ALL_TARGETS] 时在文本区展示的只读路径列表
 * @param targetOptions [SINGLE_TARGET] 时下拉候选项
 */
class AddKeyDialog(
    private val mode: AddKeyDialogMode,
    initialKey: String,
    val sourcePath: String,
    targetPathsPreview: List<String> = emptyList(),
    val targetOptions: List<TargetEntry> = emptyList(),
) : DialogWrapper(null) {

    /** 消息 key，用户可改。 */
    val keyField = JBTextField(initialKey).apply { columns = 28 }
    /** 源语言文案 / 待译值。 */
    val valueField = JBTextField("").apply { columns = 28 }
    private val sourceField = JTextField(sourcePath).apply { isEditable = false }
    private val targetCombo = com.intellij.openapi.ui.ComboBox(targetOptions.toTypedArray()).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                v: Any?,
                i: Int,
                sel: Boolean,
                f: Boolean,
            ) = super.getListCellRendererComponent(list, v, i, sel, f).apply {
                val t = v as? TargetEntry
                text = t?.let { "${it.language.displayLabelWithCode} → ${it.filePath}" } ?: ""
            }
        }
        if (targetOptions.isNotEmpty()) selectedIndex = 0
    }

    private val targetsArea = JBTextArea(targetPathsPreview.joinToString("\n")).apply {
        isEditable = false
        rows = 5
    }

    init {
        title = when (mode) {
            AddKeyDialogMode.SOURCE_ONLY -> I18nTranslateBundle.message("dialog.add.key.title")
            else -> I18nTranslateBundle.message("action.add.translate.all")
        }
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        isResizable = true
        init()
    }

    /** [SINGLE_TARGET] 模式下用户选中的目标；有效选中项时非 `null`。 */
    fun selectedTarget(): TargetEntry? =
        targetCombo.selectedItem as? TargetEntry

    /** 校验 value 非空。 */
    override fun doValidate(): ValidationInfo? {
        if (valueField.text.isBlank()) {
            return ValidationInfo(I18nTranslateBundle.message("error.value.required"), valueField)
        }
        return null
    }

    /** 按 [mode] 拼表单：key、value、源路径及可选目标区。 */
    override fun createCenterPanel(): JComponent {
        val targetScroll = JBScrollPane(targetsArea).apply {
            minimumSize = Dimension(JBUI.scale(200), JBUI.scale(80))
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(120))
        }
        val rows = buildList {
            add(LabeledFormRow(I18nTranslateBundle.message("dialog.properties.key"), keyField))
            add(LabeledFormRow(I18nTranslateBundle.message("dialog.properties.val"), valueField))
            add(LabeledFormRow(I18nTranslateBundle.message("dialog.source.path"), sourceField))
            when (mode) {
                AddKeyDialogMode.SINGLE_TARGET ->
                    add(LabeledFormRow(I18nTranslateBundle.message("dialog.target.lang"), targetCombo))
                AddKeyDialogMode.ALL_TARGETS ->
                    add(
                        LabeledFormRow(
                            I18nTranslateBundle.message("dialog.target.paths"),
                            targetScroll,
                            stretchVertically = true,
                        ),
                    )
                AddKeyDialogMode.SOURCE_ONLY -> Unit
            }
        }
        return labeledGridFormPanel(rows)
    }
}
