package com.github.xucux.i18ntranslate.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云机器翻译 HTTP（ROA）接口签名，与 `doc/阿里云通用翻译接口调用说明.md` 中 Sender 示例一致。
 */
internal object AliyunRoaSignature {

    const val ACCEPT_JSON: String = "application/json"
    const val CONTENT_TYPE_JSON_UTF8: String = "application/json;charset=utf-8"
    const val ACS_VERSION: String = "2019-01-02"

    private val gmtDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.UK)
            .withZone(ZoneId.of("GMT"))

    /** 请求体 UTF-8 字节的 MD5 再 Base64（对应文档 Content-MD5）。 */
    fun md5Base64(bodyUtf8: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bodyUtf8)
        return Base64.getEncoder().encodeToString(digest)
    }

    /** HMAC-SHA1 后 Base64；待签名字符串与密钥均按 UTF-8 编码。 */
    fun hmacSha1Base64(stringToSignUtf8: String, accessKeySecret: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(accessKeySecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(stringToSignUtf8.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
    }

    fun gmtHttpDate(instant: Instant): String = gmtDateFormatter.format(instant)

    /**
     * 构造 ROA 待签名字符串：`headerStringToSign + URI`（文档「签名计算方法」2～4 步）。
     *
     * @param requestPath URL 的 path（含 query），例如 `/api/translate/web/general`
     */
    fun buildMachineTranslationStringToSign(
        contentMd5Base64: String,
        dateGmt: String,
        signatureNonce: String,
        requestPath: String,
    ): String =
        buildString {
            appendLine("POST")
            appendLine(ACCEPT_JSON)
            appendLine(contentMd5Base64)
            appendLine(CONTENT_TYPE_JSON_UTF8)
            appendLine(dateGmt)
            appendLine("x-acs-signature-method:HMAC-SHA1")
            appendLine("x-acs-signature-nonce:$signatureNonce")
            appendLine("x-acs-version:$ACS_VERSION")
            append(requestPath)
        }

    fun authorizationValue(accessKeyId: String, signatureBase64: String): String =
        "acs $accessKeyId:$signatureBase64"

    /** 为机器翻译 JSON POST 生成鉴权相关头字段。 */
    fun signMachineTranslationPost(
        accessKeyId: String,
        accessKeySecret: String,
        requestPath: String,
        bodyUtf8: ByteArray,
        nonce: String = UUID.randomUUID().toString(),
        now: Instant = Instant.now(),
    ): AliyunMtRoaAuthHeaders {
        val contentMd5 = md5Base64(bodyUtf8)
        val dateGmt = gmtHttpDate(now)
        val stringToSign = buildMachineTranslationStringToSign(contentMd5, dateGmt, nonce, requestPath)
        val signature = hmacSha1Base64(stringToSign, accessKeySecret)
        return AliyunMtRoaAuthHeaders(
            contentMd5 = contentMd5,
            dateGmt = dateGmt,
            signatureNonce = nonce,
            authorization = authorizationValue(accessKeyId, signature),
        )
    }
}

internal data class AliyunMtRoaAuthHeaders(
    val contentMd5: String,
    val dateGmt: String,
    val signatureNonce: String,
    val authorization: String,
)
