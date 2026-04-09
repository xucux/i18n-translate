package com.github.xucux.i18ntranslate.translate

import com.github.xucux.i18ntranslate.lang.SupportedLanguage
import com.github.xucux.i18ntranslate.limit.FixedWindowRateLimiter
import com.intellij.openapi.diagnostic.Logger
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.common.profile.ClientProfile
import com.tencentcloudapi.common.profile.HttpProfile
import com.tencentcloudapi.tmt.v20180321.TmtClient
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateRequest

/**
 * 腾讯云文字翻译 `TextTranslate`（约 5 QPS，由 [FixedWindowRateLimiter] 限制）。
 *
 * @param secretId 云 API 密钥 Id
 * @param secretKey 云 API 密钥 Key
 * @param region 调用地域，默认广州
 * @param rateLimiter 调用前节流，默认每秒窗 5 次
 */
class TencentTranslationExecutor(
    private val secretId: String,
    private val secretKey: String,
    private val region: String = "ap-guangzhou",
    private val rateLimiter: FixedWindowRateLimiter = FixedWindowRateLimiter(maxPermits = 5),
) : TranslationExecutor {
    private val logger = Logger.getInstance(TencentTranslationExecutor::class.java)

    private val client: TmtClient by lazy {
        val cred = Credential(secretId, secretKey)
        val httpProfile = HttpProfile()
        httpProfile.endpoint = "tmt.tencentcloudapi.com"
        val clientProfile = ClientProfile()
        clientProfile.httpProfile = httpProfile
        TmtClient(cred, region, clientProfile)
    }

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
        logger.debug("Tencent translation request: source=${source.name}, target=${target.name}, textLength=${text.length}")
        rateLimiter.acquireBlocking()
        val req = TextTranslateRequest()
        req.sourceText = text
        req.source = source.tencentCode
        req.target = target.tencentCode
        req.projectId = 0L
        return try {
            val resp = client.TextTranslate(req)
            val out = resp.targetText
            val billedWords = resp.usedAmount?.coerceAtLeast(0L) ?: 0L
            if (out.isNullOrBlank()) {
                logger.warn("Tencent translation response is empty")
                TranslateOutcome(null, "Empty translation response")
            } else {
                logger.info(
                    "Tencent translation success: source=${source.name}, target=${target.name}, billedWords=$billedWords"
                )
                TranslateOutcome(out, null, billedWords)
            }
        } catch (e: Exception) {
            logger.error("Tencent translation failed: ${e.message}", e)
            TranslateOutcome(null, e.message ?: e.toString())
        }
    }
}
