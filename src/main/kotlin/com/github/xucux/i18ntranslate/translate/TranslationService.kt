package com.github.xucux.i18ntranslate.translate

import com.github.xucux.i18ntranslate.lang.SupportedLanguage

/**
 * 单次翻译结果：[text] 非空表示成功，否则参考 [errorMessage]。
 * [billedWords]：成功时用于累加的数值（阿里云 WordCount、腾讯云 UsedAmount，后者按接口释义为字符数）。
 */
data class TranslateOutcome(
    /** 译文；非空表示调用成功。 */
    val text: String?,
    /** 失败时的错误描述；成功时为 `null`。 */
    val errorMessage: String?,
    /** 成功时云 API 返回的可累计计数（阿里云 WordCount / 腾讯云 UsedAmount）。 */
    val billedWords: Long? = null,
) {
    /** 是否与一次成功翻译对应（等价于 [text] != null）。 */
    val ok: Boolean get() = text != null
}

/**
 * 抽象翻译服务：由具体引擎实现，上层仅关心语种与文本。
 */
fun interface TranslationExecutor {
    /**
     * 将 [text] 从 [source] 译至 [target]。
     * @return 成功时 [TranslateOutcome.text] 非空且可带 [TranslateOutcome.billedWords]
     */
    fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): TranslateOutcome
}
