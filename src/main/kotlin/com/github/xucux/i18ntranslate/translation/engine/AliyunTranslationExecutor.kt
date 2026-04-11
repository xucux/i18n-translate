package com.github.xucux.i18ntranslate.translation.engine

import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.translation.api.TranslateOutcome
import com.github.xucux.i18ntranslate.translation.api.TranslationExecutor
import com.github.xucux.i18ntranslate.translation.remote.AliyunMtApiClient
import com.github.xucux.i18ntranslate.util.FixedWindowRateLimiter
import com.intellij.openapi.diagnostic.Logger

/**
 * 阿里云机器翻译通用版（HTTP `/api/translate/web/general` + ROA 签名，约 50 QPS，由 [FixedWindowRateLimiter] 限制）。
 *
 * @param accessKeyId RAM 用户 AccessKeyId
 * @param accessKeySecret RAM 用户 AccessKeySecret
 * @param rateLimiter 调用前节流，默认每 1s 窗口内最多 50 次
 */
class AliyunTranslationExecutor(
    private val accessKeyId: String,
    private val accessKeySecret: String,
    private val rateLimiter: FixedWindowRateLimiter = FixedWindowRateLimiter(maxPermits = 50),
) : TranslationExecutor {
    private val logger = Logger.getInstance(AliyunTranslationExecutor::class.java)

    /** [TranslationExecutor.translate]：通用文本场景，`formatType=text`。 */
    override fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): TranslateOutcome {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
            logger.warn("Aliyun credentials are empty")
            return TranslateOutcome(null, "Alibaba Cloud credentials are empty")
        }
        logger.debug("Aliyun translation request: source=${source.name}, target=${target.name}, textLength=${text.length}")
        rateLimiter.acquireBlocking()
        val result = AliyunMtApiClient.translateGeneral(
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            sourceLanguage = source.aliyunCode,
            targetLanguage = target.aliyunCode,
            sourceText = text,
        )
        return result.fold(
            onSuccess = { ok ->
                logger.info(
                    "Aliyun translation success: source=${source.name}, target=${target.name}, billedWords=${ok.wordCount}",
                )
                TranslateOutcome(ok.translated, null, ok.wordCount)
            },
            onFailure = { e ->
                logger.error("Aliyun translation failed: ${e.message}", e)
                TranslateOutcome(null, e.message ?: e.toString())
            },
        )
    }
}
