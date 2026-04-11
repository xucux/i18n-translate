package com.github.xucux.i18ntranslate.translation.remote

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.xucux.i18ntranslate.util.AliyunRoaSignature
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * 阿里云机器翻译通用版 HTTP 接口（JSON Body + ROA 签名）。
 *
 * 接入地址与签名见 `doc/阿里云通用翻译接口调用说明.md`（`/api/translate/web/general`）。
 */
internal object AliyunMtApiClient {
    private val json = ObjectMapper()

    private const val TRANSLATE_GENERAL_URL = "https://mt.cn-hangzhou.aliyuncs.com/api/translate/web/general"
    private const val HOST = "mt.cn-hangzhou.aliyuncs.com"

    private val generalUri: URI = URI.create(TRANSLATE_GENERAL_URL)
    private val generalPath: String = generalUri.path

    data class TranslateOk(val translated: String, val wordCount: Long)

    fun translateGeneral(
        accessKeyId: String,
        accessKeySecret: String,
        sourceLanguage: String,
        targetLanguage: String,
        sourceText: String,
    ): Result<TranslateOk> {
        val bodyNode =
            json.createObjectNode().apply {
                put("FormatType", "text")
                put("SourceLanguage", sourceLanguage)
                put("TargetLanguage", targetLanguage)
                put("SourceText", sourceText)
                put("Scene", "general")
            }
        val bodyBytes = json.writeValueAsBytes(bodyNode)

        val auth =
            AliyunRoaSignature.signMachineTranslationPost(
                accessKeyId = accessKeyId,
                accessKeySecret = accessKeySecret,
                requestPath = generalPath,
                bodyUtf8 = bodyBytes,
            )

        val request =
            HttpRequest.newBuilder()
                .uri(generalUri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", AliyunRoaSignature.ACCEPT_JSON)
                .header("Content-Type", AliyunRoaSignature.CONTENT_TYPE_JSON_UTF8)
                .header("Content-MD5", auth.contentMd5)
                .header("Date", auth.dateGmt)
                .header("Host", HOST)
                .header("Authorization", auth.authorization)
                .header("x-acs-signature-nonce", auth.signatureNonce)
                .header("x-acs-signature-method", "HMAC-SHA1")
                .header("x-acs-version", AliyunRoaSignature.ACS_VERSION)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build()

        return runCatching {
            val response = PluginHttpApiClient.send(request)
            val text = response.body()
            if (response.statusCode() != 200) {
                throwFailureFromBody(text, response.statusCode())
            }
            parseSuccessBody(text)
        }
    }

    private fun parseSuccessBody(text: String): TranslateOk {
        val root = json.readTree(text)
        if (root.hasNonNull("errorCode")) {
            val code = root.path("errorCode").asText()
            val msg = root.path("errorMsg").asText("")
            error("Aliyun API error $code: $msg")
        }
        val code = aliyunCode(root)
        if (code != 200) {
            val msg = root.path("Message").asText("Unknown error")
            error("Aliyun API error $code: $msg")
        }
        val data = root.path("Data")
        val translated = data.path("Translated").asText("").trim()
        if (translated.isEmpty()) error("Empty translation")
        val wc = parseWordCount(data.path("WordCount"))
        return TranslateOk(translated, wc)
    }

    private fun throwFailureFromBody(text: String, httpStatus: Int): Nothing {
        runCatching {
            val root = json.readTree(text)
            if (root.hasNonNull("errorCode")) {
                error(
                    "Aliyun API ${root.path("errorCode").asText()}: ${root.path("errorMsg").asText()}",
                )
            }
            val code = aliyunCode(root)
            if (code != 0 && code != 200) {
                error("Aliyun API error $code: ${root.path("Message").asText()}")
            }
        }
        error("HTTP $httpStatus: $text")
    }

    private fun aliyunCode(root: JsonNode): Int {
        val n = root.path("Code")
        return when {
            n.isMissingNode || n.isNull -> 0
            n.isInt -> n.asInt()
            n.isIntegralNumber -> n.asLong().toInt()
            else -> n.asText("").toIntOrNull() ?: 0
        }
    }

    private fun parseWordCount(node: JsonNode): Long {
        if (node.isMissingNode || node.isNull) return 0L
        return when {
            node.isNumber -> node.asLong().coerceAtLeast(0L)
            else -> node.asText("").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }
}
