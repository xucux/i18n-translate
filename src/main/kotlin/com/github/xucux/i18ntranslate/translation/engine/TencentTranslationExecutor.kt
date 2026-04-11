package com.github.xucux.i18ntranslate.translation.engine

import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.translation.api.TranslateOutcome
import com.github.xucux.i18ntranslate.translation.api.TranslationExecutor
import com.github.xucux.i18ntranslate.translation.remote.TencentTmtTc3ApiClient
import com.github.xucux.i18ntranslate.util.FixedWindowRateLimiter
import com.intellij.openapi.diagnostic.Logger

/**
 * 腾讯云文字翻译 `TextTranslate`（约 5 QPS，由 [FixedWindowRateLimiter] 限制）。
 *
 * @param secretId 云 API 密钥 Id
 * @param secretKey 云 API 密钥 Key
 * @param region 调用地域（`X-TC-Region`，如 `ap-guangzhou`），来自全局设置
 * @param rateLimiter 调用前节流，默认每秒窗 5 次
 */
class TencentTranslationExecutor(
    private val secretId: String,
    private val secretKey: String,
    private val region: String,
    private val rateLimiter: FixedWindowRateLimiter = FixedWindowRateLimiter(maxPermits = 5),
) : TranslationExecutor {
    private val logger = Logger.getInstance(TencentTranslationExecutor::class.java)

    /** [TranslationExecutor.translate]：`TextTranslate`，`projectId=0`。 */
    override fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): TranslateOutcome {
        if (secretId.isBlank() || secretKey.isBlank()) {
            logger.warn("Tencent credentials are empty")
            return TranslateOutcome(null, "Tencent Cloud credentials are empty")
        }
        logger.debug(
            "Tencent translation request: region=$region, source=${source.name}, target=${target.name}, textLength=${text.length}",
        )
        rateLimiter.acquireBlocking()
        val result = TencentTmtTc3ApiClient.textTranslate(
            secretId = secretId,
            secretKey = secretKey,
            region = region,
            sourceText = text,
            source = source.tencentCode,
            target = target.tencentCode,
        )
        return result.fold(
            onSuccess = { ok ->
                logger.info(
                    "Tencent translation success: source=${source.name}, target=${target.name}, billedWords=${ok.usedAmount}",
                )
                TranslateOutcome(ok.targetText, null, ok.usedAmount)
            },
            onFailure = { e ->
                logger.error("Tencent translation failed: ${e.message}", e)
                TranslateOutcome(null, e.message ?: e.toString())
            },
        )
    }
}
