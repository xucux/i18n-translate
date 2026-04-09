package com.github.xucux.i18ntranslate.translate

import com.aliyun.alimt20181012.Client
import com.aliyun.alimt20181012.models.TranslateGeneralRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import com.github.xucux.i18ntranslate.lang.SupportedLanguage
import com.github.xucux.i18ntranslate.limit.FixedWindowRateLimiter
import com.intellij.openapi.diagnostic.Logger

/**
 * 阿里云机器翻译通用版 `TranslateGeneral`（约 50 QPS，由 [FixedWindowRateLimiter] 限制）。
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
    private val client: Client = run {
        val config = Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret)
        config.endpoint = "mt.aliyuncs.com"
        Client(config)
    }

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
        val req = TranslateGeneralRequest()
            .setSourceText(text)
            .setFormatType("text")
            .setSourceLanguage(source.aliyunCode)
            .setTargetLanguage(target.aliyunCode)
            .setScene("general")
        return try {
            val resp = client.translateGeneralWithOptions(req, RuntimeOptions())
            val data = resp.body?.data
            val translated = data?.translated
            val billedWords = parseAliyunWordCount(data?.wordCount)
            if (translated.isNullOrBlank()) {
                logger.warn("Aliyun translation response is empty")
                TranslateOutcome(null, "Empty translation response")
            } else {
                logger.info(
                    "Aliyun translation success: source=${source.name}, target=${target.name}, billedWords=$billedWords"
                )
                TranslateOutcome(translated, null, billedWords)
            }
        } catch (e: Exception) {
            logger.error("Aliyun translation failed: ${e.message}", e)
            TranslateOutcome(null, e.message ?: e.toString())
        }
    }

    /** 将响应中的 `WordCount`（常见为字符串）解析为非负 long，失败则 0。 */
    private fun parseAliyunWordCount(raw: Any?): Long {
        if (raw == null) return 0L
        return when (raw) {
            is Number -> raw.toLong().coerceAtLeast(0L)
            is String -> raw.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            else -> raw.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }
}
