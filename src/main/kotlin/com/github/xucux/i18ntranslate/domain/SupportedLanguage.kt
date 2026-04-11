package com.github.xucux.i18ntranslate.domain

/**
 * 插件支持的语种：展示使用母语（[nativeName]）+ API 代码（[aliyunCode]，与阿里云文档一致；繁体等个别语种腾讯代码略有不同）。
 */
enum class SupportedLanguage(
    val nativeName: String,
    val aliyunCode: String,
    val tencentCode: String,
) {
    CHINESE("简体中文", "zh", "zh"),
    CHINESE_TRADITIONAL("繁體中文", "zh-tw", "zh-TW"),
    ENGLISH("English", "en", "en"),
    /**
     * 法语
     */
    FRENCH("Français", "fr", "fr"),
    /**
     * 俄语
     */
    RUSSIAN("Русский", "ru", "ru"),
    /**
     * 德语
     */
    GERMAN("Deutsch", "de", "de"),
    /**
     * 西班牙语
     */
    SPANISH("Español", "es", "es"),
    /**
     * 意大利语
     */
    ITALIAN("Italiano", "it", "it"),
    /**
     * 土耳其语
     */
    TURKISH("Türkçe", "tr", "tr"),
    /**
     * 阿拉伯语
     */
    ARABIC("العربية", "ar", "ar"),
    /**
     * 印地语
     */
    HINDI("हिन्दी", "hi", "hi"),
    /**
     * 日语
     */
    JAPANESE("日本語", "ja", "ja"),
    /**
     * 越南语
     */
    VIETNAMESE("Tiếng Việt", "vi", "vi"),
    /**
     * 韩语
     */
    KOREAN("한국어", "ko", "ko"),
    /**
     * 泰语
     */
    THAI("ไทย", "th", "th"),
    /**
     * 马来语
     */
    MALAY("Bahasa Melayu", "ms", "ms"),
    /**
     * 印尼语
     */
    INDONESIAN("Bahasa Indonesia", "id", "id"),
    /**
     * 葡萄牙语
     */
    PORTUGUESE("Português", "pt", "pt"),
    ;

    /** 设置页、下拉、菜单等：`简体中文 (zh)`。 */
    val displayLabelWithCode: String
        get() = "$nativeName ($aliyunCode)"

    companion object {
        /** 按阿里云机器翻译语言代码解析（忽略大小写），无法识别时返回 `null`。 */
        fun fromAliyunCode(code: String): SupportedLanguage? =
            values().firstOrNull { it.aliyunCode.equals(code, ignoreCase = true) }

        /** 按腾讯云 TMT 语言代码解析（忽略大小写），无法识别时返回 `null`。 */
        fun fromTencentCode(code: String): SupportedLanguage? =
            values().firstOrNull { it.tencentCode.equals(code, ignoreCase = true) }
    }

    /** DeepL source language code（source_lang）。 */
    fun deepLSourceCode(): String = when (this) {
        CHINESE, CHINESE_TRADITIONAL -> "ZH"
        ENGLISH -> "EN"
        FRENCH -> "FR"
        RUSSIAN -> "RU"
        GERMAN -> "DE"
        SPANISH -> "ES"
        ITALIAN -> "IT"
        TURKISH -> "TR"
        ARABIC -> "AR"
        HINDI -> "HI"
        JAPANESE -> "JA"
        VIETNAMESE -> "VI"
        KOREAN -> "KO"
        THAI -> "TH"
        MALAY -> "MS"
        INDONESIAN -> "ID"
        PORTUGUESE -> "PT"
    }

    /** DeepL target language code（target_lang）。 */
    fun deepLTargetCode(): String = when (this) {
        CHINESE -> "ZH-HANS"
        CHINESE_TRADITIONAL -> "ZH-HANT"
        ENGLISH -> "EN"
        FRENCH -> "FR"
        RUSSIAN -> "RU"
        GERMAN -> "DE"
        SPANISH -> "ES"
        ITALIAN -> "IT"
        TURKISH -> "TR"
        ARABIC -> "AR"
        HINDI -> "HI"
        JAPANESE -> "JA"
        VIETNAMESE -> "VI"
        KOREAN -> "KO"
        THAI -> "TH"
        MALAY -> "MS"
        INDONESIAN -> "ID"
        PORTUGUESE -> "PT"
    }
}
