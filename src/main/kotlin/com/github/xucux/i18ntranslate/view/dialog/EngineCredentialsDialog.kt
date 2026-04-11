package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.TencentTranslateRegion
import com.github.xucux.i18ntranslate.config.TranslateEngine
import com.github.xucux.i18ntranslate.translation.remote.DeepLApiClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 设置中「引擎凭证」弹窗：保存 AccessKeyId/SecretId 与密钥（文本需求 2.1.2 弹窗 A）。
 *
 * @param engine 决定标题文案与帮助链接跳转
 * @param initialId 打开时预填的访问标识
 * @param initialSecret 打开时预填的密钥
 * @param initialTencentRegionCode 仅腾讯云：当前保存的 `X-TC-Region` 代码
 */
class EngineCredentialsDialog(
    private val engine: TranslateEngine,
    initialId: String,
    initialSecret: String,
    initialTencentRegionCode: String = TencentTranslateRegion.DEFAULT_CODE,
) : DialogWrapper(null) {

    private val idField = JBTextField(initialId, 32)
    private val secretField = JBPasswordField().apply {
        text = initialSecret
    }
    private val tencentRegionCombo: ComboBox<TencentTranslateRegion>? =
        if (engine == TranslateEngine.TENCENT) {
            ComboBox(TencentTranslateRegion.values()).apply {
                renderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                        text = (value as? TencentTranslateRegion)?.displayComboLabel() ?: ""
                    }
                }
                setMinimumAndPreferredWidth(JBUI.scale(480))
            }
        } else {
            null
        }
    private val usageValueLabel = JBLabel("-")
    private val quotaValueLabel = JBLabel("-")

    /** 用户确认后：AccessKeyId 或 SecretId（已 trim）。 */
    val accessId: String get() = idField.text.trim()
    /** 用户确认后：密钥（已 trim）。 */
    val accessSecret: String get() = String(secretField.password).trim()

    /** 用户确认后：腾讯云地域代码（非腾讯云引擎时为默认广州）。 */
    val tencentRegionCode: String
        get() =
            (tencentRegionCombo?.selectedItem as? TencentTranslateRegion)?.code
                ?: TencentTranslateRegion.DEFAULT_CODE

    init {
        tencentRegionCombo?.selectedItem = TencentTranslateRegion.fromCodeOrDefault(initialTencentRegionCode)
        title = I18nTranslateBundle.message("settings.credentials.title", engine.displayZh() + " / " + engine.displayEn())
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        if (engine == TranslateEngine.DEEPL) {
            secretField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = refreshDeepLUsage()
                override fun removeUpdate(e: DocumentEvent?) = refreshDeepLUsage()
                override fun changedUpdate(e: DocumentEvent?) = refreshDeepLUsage()
            })
        }
        init()
        if (engine == TranslateEngine.DEEPL) {
            refreshDeepLUsage()
        }
    }

    /** 构建 Id、密钥输入与服务商帮助超链接。 */
    override fun createCenterPanel(): JComponent {
        val helpUrl = when (engine) {
            TranslateEngine.ALIYUN -> "https://www.aliyun.com/product/ai/base_alimt"
            TranslateEngine.TENCENT -> "https://cloud.tencent.com/product/tmt"
            TranslateEngine.DEEPL -> "https://developers.deepl.com/api-reference/translate"
        }
        val helpLink = HyperlinkLabel(I18nTranslateBundle.message("settings.credentials.help"))
        helpLink.setHyperlinkTarget(helpUrl)
        if (engine == TranslateEngine.DEEPL) {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.auth.key"), secretField)
                .addComponent(JBLabel(I18nTranslateBundle.message("settings.credentials.deepl.hint")))
                .addComponent(helpLink)
                .addComponent(JPanel().apply { border = JBUI.Borders.emptyTop(8) })
                .addSeparator()
                .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.usage"), usageValueLabel)
                .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.quota"), quotaValueLabel)
                .panel
        }
        if (engine == TranslateEngine.TENCENT) {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.id"), idField)
                .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.secret"), secretField)
                .addLabeledComponent(I18nTranslateBundle.message("settings.tencent.region"), tencentRegionCombo!!)
                .addComponent(JBLabel())
                .addComponent(helpLink)
                .panel
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.id"), idField)
            .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.secret"), secretField)
            .addComponent(JBLabel())
            .addComponent(helpLink)
            .panel
    }

    private fun refreshDeepLUsage() {
        val key = accessSecret
        if (key.isBlank()) {
            usageValueLabel.text = "-"
            quotaValueLabel.text = "-"
            return
        }
        usageValueLabel.text = I18nTranslateBundle.message("settings.credentials.loading")
        quotaValueLabel.text = I18nTranslateBundle.message("settings.credentials.loading")
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = DeepLApiClient(key).fetchUsage()
            SwingUtilities.invokeLater {
                if (isDisposed) return@invokeLater
                result.fold(
                    onSuccess = { usage ->
                        usageValueLabel.text = "${usage.characterCount} ${I18nTranslateBundle.message("settings.credentials.characters")}"
                        quotaValueLabel.text = "${usage.characterLimit} ${I18nTranslateBundle.message("settings.credentials.characters")}"
                    },
                    onFailure = {
                        val failText = I18nTranslateBundle.message("settings.credentials.unavailable")
                        usageValueLabel.text = failText
                        quotaValueLabel.text = failText
                    },
                )
            }
        }
    }
}
