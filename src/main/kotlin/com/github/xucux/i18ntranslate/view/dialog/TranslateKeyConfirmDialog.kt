package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * 从资源文件发起翻译前的确认：可改 value；可选展示源路径与多行目标列表（译到全部目标时）。
 *
 * @param title 对话框标题
 * @param initialValue 打开时预填的待译文案
 * @param targetPath 主展示的单个目标路径（只读）
 * @param extraSourcePath 非空时在表单中增加只读源路径一行
 * @param extraTargetPaths 非空时用多行只读区展示全部目标
 */
class TranslateKeyConfirmDialog(
    title: String,
    /** 当前消息 key（只读展示）。 */
    val messageKey: String,
    initialValue: String,
    targetPath: String,
    private val extraSourcePath: String? = null,
    private val extraTargetPaths: List<String> = emptyList(),
) : DialogWrapper(null) {

    private val keyField = JTextField(messageKey).apply { isEditable = false }
    /** 待发送给翻译 API 的源文案，用户可编辑。 */
    val valueField = JBTextField(initialValue).apply { columns = 28 }
    private val targetField = JTextField(targetPath).apply { isEditable = false }
    private val sourceField = JTextField(extraSourcePath.orEmpty()).apply { isEditable = false }
    private val extrasArea = JBTextArea(extraTargetPaths.joinToString("\n")).apply {
        isEditable = false
        rows = 4
    }

    init {
        this.title = title
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        isResizable = true
        init()
    }

    /** 校验 value 非空。 */
    override fun doValidate(): ValidationInfo? {
        if (valueField.text.isBlank()) {
            return ValidationInfo(I18nTranslateBundle.message("error.value.required"), valueField)
        }
        return null
    }

    /** 构建 key/value 与可选的源、多目标只读信息。 */
    override fun createCenterPanel(): JComponent {
        val extrasScroll = JBScrollPane(extrasArea).apply {
            minimumSize = Dimension(JBUI.scale(200), JBUI.scale(72))
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(100))
        }
        val rows = buildList {
            add(LabeledFormRow(I18nTranslateBundle.message("dialog.properties.key"), keyField))
            add(LabeledFormRow(I18nTranslateBundle.message("dialog.properties.val"), valueField))
            add(LabeledFormRow(I18nTranslateBundle.message("settings.target.path"), targetField))
            if (!extraSourcePath.isNullOrBlank()) {
                add(LabeledFormRow(I18nTranslateBundle.message("dialog.source.path"), sourceField))
            }
            if (extraTargetPaths.isNotEmpty()) {
                add(
                    LabeledFormRow(
                        I18nTranslateBundle.message("dialog.target.paths"),
                        extrasScroll,
                        stretchVertically = true,
                    ),
                )
            }
        }
        return labeledGridFormPanel(rows)
    }
}
