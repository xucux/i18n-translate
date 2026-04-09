package com.github.xucux.i18ntranslate.bundle

import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.github.xucux.i18ntranslate.config.PluginUiLanguage
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.MissingResourceException
import java.util.ResourceBundle

private const val BUNDLE = "messages.I18nTranslateBundle"

/**
 * 插件 UI 文案：`messages/I18nTranslateBundle*.properties`。
 * 语言由 [GlobalPluginConfigService] 中的 [com.github.xucux.i18ntranslate.config.GlobalPluginState.uiLanguage] 决定（默认英文）；
 * 设置页预览时可通过 [settingsPreviewLanguage] 覆盖。
 */
object I18nTranslateBundle {

    /**
     * 设置界面中尚未 Apply 时，用于即时刷新标签；关闭设置或 Apply 后应置回 `null`。
     */
    @JvmField
    var settingsPreviewLanguage: PluginUiLanguage? = null

    /** 当前应加载的 ResourceBundle 语言：优先 [settingsPreviewLanguage]，否则读全局配置。 */
    private fun effectiveLanguage(): PluginUiLanguage {
        settingsPreviewLanguage?.let { return it }
        val app = ApplicationManager.getApplication() ?: return PluginUiLanguage.ENGLISH
        if (app.isDisposed) return PluginUiLanguage.ENGLISH
        return runCatching {
            app.getService(GlobalPluginConfigService::class.java).getState().uiLanguage
        }.getOrElse { PluginUiLanguage.ENGLISH }
    }

    /** 按 [effectiveLanguage] 解析的 `I18nTranslateBundle` 实例。 */
    private fun bundle(): ResourceBundle =
        ResourceBundle.getBundle(
            BUNDLE,
            effectiveLanguage().locale,
            I18nTranslateBundle::class.java.classLoader,
        )

    /**
     * 取国际化字符串；若 key 缺失返回 `!key!` 占位。
     * @param params [MessageFormat] 占位符参数
     */
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String {
        val pattern = try {
            bundle().getString(key)
        } catch (_: MissingResourceException) {
            return "!$key!"
        }
        return if (params.isEmpty()) pattern else MessageFormat.format(pattern, *params)
    }
}
