package com.github.xucux.i18ntranslate.config

import java.util.Locale

/** 插件设置页与各 UI 文案使用的语言（默认英文资源，与 IDE 界面语言无关）。 */
enum class PluginUiLanguage(val locale: Locale) {
    /** 使用 `I18nTranslateBundle.properties`（默认）。 */
    ENGLISH(Locale.ROOT),
    /** 使用 `I18nTranslateBundle_zh_CN.properties`。 */
    CHINESE(Locale.SIMPLIFIED_CHINESE),
}
