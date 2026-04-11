package com.github.xucux.i18ntranslate.config

/** 翻译服务商（与设置中下拉项及凭证存储字段对应）。 */
enum class TranslateEngine {
    ALIYUN,
    TENCENT,
    DEEPL,
    ;

    /** 设置页、菜单等场景下的中文展示名。 */
    fun displayZh(): String = when (this) {
        ALIYUN -> "阿里云翻译"
        TENCENT -> "腾讯翻译"
        DEEPL -> "DeepL 翻译"
    }

    /** 设置页、菜单等场景下的英文展示名。 */
    fun displayEn(): String = when (this) {
        ALIYUN -> "Alibaba Cloud Translate"
        TENCENT -> "Tencent Cloud TMT"
        DEEPL -> "DeepL Translate"
    }
}
