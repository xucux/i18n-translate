package com.github.xucux.i18ntranslate.translation.engine

import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.translation.api.TranslateOutcome
import com.github.xucux.i18ntranslate.translation.api.TranslationExecutor
import com.github.xucux.i18ntranslate.translation.remote.DeepLApiClient
import com.intellij.openapi.diagnostic.Logger

/**
 * DeepL 文本翻译执行器，对接 `/v2/translate`。
 */
class DeepLTranslationExecutor(
    private val authKey: String,
) : TranslationExecutor {
    private val logger = Logger.getInstance(DeepLTranslationExecutor::class.java)
    private val apiClient = DeepLApiClient(authKey)

    override fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): TranslateOutcome {
        if (authKey.isBlank()) {
            logger.warn("DeepL auth key is empty")
            return TranslateOutcome(null, "DeepL auth key is empty")
        }
        logger.debug("DeepL translation request: source=${source.name}, target=${target.name}, textLength=${text.length}")
        val result = apiClient.translateSingle(
            text = text,
            sourceLang = source.deepLSourceCode(),
            targetLang = target.deepLTargetCode(),
        )
        if (result.text != null) {
            logger.info(
                "DeepL translation success: source=${source.name}, target=${target.name}, billedCharacters=${result.billedCharacters}",
            )
            return TranslateOutcome(result.text, null, result.billedCharacters)
        }
        logger.warn("DeepL translation failed: ${result.error}")
        return TranslateOutcome(null, result.error ?: "Unknown DeepL error")
    }
}
