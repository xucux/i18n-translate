package com.github.xucux.i18ntranslate.ui

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import kotlin.math.max
import kotlin.math.min
import javax.swing.JComponent
import javax.swing.JTextField
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo

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

    private val adaptiveColumns = calculateAdaptiveColumns(messageKey, initialValue, targetPath, extraSourcePath.orEmpty())

    private val keyField = JTextField(messageKey).apply { isEditable = false }
    /** 待发送给翻译 API 的源文案，用户可编辑。 */
    val valueField = JBTextField(initialValue, adaptiveColumns)
    private val targetField = JTextField(targetPath).apply { isEditable = false }
    private val sourceField = JTextField(extraSourcePath.orEmpty()).apply { isEditable = false }
    private val extrasArea = JBTextArea(extraTargetPaths.joinToString("\n")).apply {
        isEditable = false
        rows = 4
        columns = adaptiveColumns
    }

    init {
        this.title = title
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        isResizable = true
        applyAdaptiveWidth(valueField, adaptiveColumns)
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
        var b = FormBuilder.createFormBuilder()
            .addLabeledComponent(I18nTranslateBundle.message("dialog.properties.key"), keyField)
            .addLabeledComponent(I18nTranslateBundle.message("dialog.properties.val"), valueField)
            .addLabeledComponent(I18nTranslateBundle.message("settings.target.path"), targetField)
        if (!extraSourcePath.isNullOrBlank()) {
            b = b.addLabeledComponent(I18nTranslateBundle.message("dialog.source.path"), sourceField)
        }
        if (extraTargetPaths.isNotEmpty()) {
            b = b.addLabeledComponent(
                I18nTranslateBundle.message("dialog.target.paths"),
                com.intellij.ui.components.JBScrollPane(extrasArea),
            )
        }
        return b.panel
    }

    /**
     * 根据内容长度预估合适列数，给输入框一个更自然的初始宽度。
     * 这里做上下限约束，避免因为超长文本导致对话框过宽。
     */
    private fun calculateAdaptiveColumns(vararg samples: String): Int {
        val longest = samples.maxOfOrNull { it.length } ?: 0
        return (longest + 8).coerceIn(48, 96)
    }

    /**
     * 某些主题/布局下 columns 可能被压缩；增加最小宽度兜底，避免输入框偶发过短。
     */
    private fun applyAdaptiveWidth(field: JBTextField, columns: Int) {
        val fm = field.getFontMetrics(field.font)
        val charWidth = max(fm.charWidth('m'), fm.charWidth('中'))
        val estimated = charWidth * columns + JBUI.scale(24)
        val minWidth = JBUI.scale(420)
        val maxWidth = JBUI.scale(900)
        val width = min(max(estimated, minWidth), maxWidth)
        val h = field.preferredSize.height
        field.preferredSize = Dimension(width, h)
        field.minimumSize = Dimension(JBUI.scale(320), h)
    }
}
