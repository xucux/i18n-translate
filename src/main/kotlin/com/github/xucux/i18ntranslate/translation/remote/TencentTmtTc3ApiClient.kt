package com.github.xucux.i18ntranslate.translation.remote

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云 TMT `TextTranslate`（TC3-HMAC-SHA256），不依赖官方 SDK。
 */
internal object TencentTmtTc3ApiClient {
    private val json = ObjectMapper()
    private const val HOST = "tmt.tencentcloudapi.com"
    private const val SERVICE = "tmt"
    private const val VERSION = "2018-03-21"
    private const val ACTION = "TextTranslate"
    private const val ENDPOINT = "https://$HOST/"

    data class TranslateOk(val targetText: String, val usedAmount: Long)

    fun textTranslate(
        secretId: String,
        secretKey: String,
        region: String,
        sourceText: String,
        source: String,
        target: String,
    ): Result<TranslateOk> {
        val payload = json.writeValueAsString(
            mapOf(
                "SourceText" to sourceText,
                "Source" to source,
                "Target" to target,
                "ProjectId" to 0,
            ),
        )
        val now = Instant.now()
        val timestamp = now.epochSecond.toString()
        val date = DateTimeFormatter.ISO_LOCAL_DATE.format(now.atZone(ZoneOffset.UTC))

        val canonicalRequest = buildCanonicalRequest(timestamp, region, payload)
        val hashedCanonical = sha256Hex(canonicalRequest)
        val credentialScope = "$date/$SERVICE/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n$hashedCanonical"

        val signingKey = deriveSigningKey(secretKey, date, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val signedHeaders =
            "content-type;host;x-tc-action;x-tc-region;x-tc-timestamp;x-tc-version"
        val authorization =
            "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val request =
            Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Host", HOST)
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", timestamp)
                .header("X-TC-Region", region)
                .post(payload.toRequestBody(jsonMedia))
                .build()

        return runCatching {
            val response = PluginHttpApiClient.send(request)
            val text = response.body
            if (response.statusCode != 200) {
                error("HTTP ${response.statusCode}: $text")
            }
            parseResponse(text)
        }
    }

    private fun buildCanonicalRequest(timestamp: String, region: String, payload: String): String {
        val hashedPayload = sha256Hex(payload)
        val actionLower = ACTION.lowercase()
        val headers = buildString {
            append("content-type:application/json; charset=utf-8\n")
            append("host:").append(HOST).append('\n')
            append("x-tc-action:").append(actionLower).append('\n')
            append("x-tc-region:").append(region).append('\n')
            append("x-tc-timestamp:").append(timestamp).append('\n')
            append("x-tc-version:").append(VERSION).append('\n')
        }
        val signedHeaders =
            "content-type;host;x-tc-action;x-tc-region;x-tc-timestamp;x-tc-version"
        return "POST\n/\n\n$headers\n$signedHeaders\n$hashedPayload"
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(s.toByteArray(StandardCharsets.UTF_8))
        return dig.joinToString("") { "%02x".format(it) }
    }

    private fun deriveSigningKey(secretKey: String, date: String, service: String): ByteArray {
        val kSecret = ("TC3$secretKey").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256Raw(kSecret, date)
        val kService = hmacSha256Raw(kDate, service)
        return hmacSha256Raw(kService, "tc3_request")
    }

    private fun hmacSha256Raw(key: ByteArray, msg: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, msg: String): String {
        val raw = hmacSha256Raw(key, msg)
        return raw.joinToString("") { "%02x".format(it) }
    }

    private fun parseResponse(text: String): TranslateOk {
        val root = json.readTree(text)
        val resp = root.path("Response")
        val err = resp.path("Error")
        if (!err.isMissingNode && err.isObject && err.path("Code").asText("").isNotEmpty()) {
            val code = err.path("Code").asText("")
            val msg = err.path("Message").asText("")
            error("Tencent API error $code: $msg")
        }
        val out = resp.path("TargetText").asText("").trim()
        if (out.isEmpty()) error("Empty translation")
        val used = resp.path("UsedAmount").asLong(0L).coerceAtLeast(0L)
        return TranslateOk(out, used)
    }
}
