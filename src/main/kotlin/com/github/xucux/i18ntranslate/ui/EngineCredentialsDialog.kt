package com.github.xucux.i18ntranslate.ui

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.TranslateEngine
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * 设置中「引擎凭证」弹窗：保存 AccessKeyId/SecretId 与密钥（文本需求 2.1.2 弹窗 A）。
 *
 * @param engine 决定标题文案与帮助链接跳转
 * @param initialId 打开时预填的访问标识
 * @param initialSecret 打开时预填的密钥
 */
class EngineCredentialsDialog(
    private val engine: TranslateEngine,
    initialId: String,
    initialSecret: String,
) : DialogWrapper(null) {

    private val idField = JBTextField(initialId, 32)
    private val secretField = JBPasswordField().apply {
        text = initialSecret
    }

    /** 用户确认后：AccessKeyId 或 SecretId（已 trim）。 */
    val accessId: String get() = idField.text.trim()
    /** 用户确认后：密钥（已 trim）。 */
    val accessSecret: String get() = String(secretField.password).trim()

    init {
        title = I18nTranslateBundle.message("settings.credentials.title", engine.displayZh() + " / " + engine.displayEn())
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        init()
    }

    /** 构建 Id、密钥输入与服务商帮助超链接。 */
    override fun createCenterPanel(): JComponent {
        val helpUrl = when (engine) {
            TranslateEngine.ALIYUN -> "https://www.aliyun.com/product/ai/base_alimt"
            TranslateEngine.TENCENT -> "https://cloud.tencent.com/product/tmt"
        }
        val helpLink = HyperlinkLabel(I18nTranslateBundle.message("settings.credentials.help"))
        helpLink.setHyperlinkTarget(helpUrl)
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.id"), idField)
            .addLabeledComponent(I18nTranslateBundle.message("settings.credentials.secret"), secretField)
            .addComponent(JBLabel()) // spacer
            .addComponent(helpLink)
            .panel
    }
}
