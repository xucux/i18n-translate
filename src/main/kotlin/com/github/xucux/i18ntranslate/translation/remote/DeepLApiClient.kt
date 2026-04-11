package com.github.xucux.i18ntranslate.translation.remote

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * DeepL HTTP 客户端：封装 `translate` 与 `usage` 两个接口。
 */
class DeepLApiClient(
    private val authKey: String,
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    data class UsageResult(
        val characterCount: Long,
        val characterLimit: Long,
    )

    data class TranslateResult(
        val text: String?,
        val billedCharacters: Long,
        val error: String?,
    )

    fun fetchUsage(): Result<UsageResult> {
        if (authKey.isBlank()) return Result.failure(IllegalArgumentException("DeepL auth key is empty"))
        return runCatching {
            val body = execute("GET", "/v2/usage", null)
            val root = mapper.readTree(body)
            val used = root.path("character_count").asLong(0L).coerceAtLeast(0L)
            val limit = root.path("character_limit").asLong(0L).coerceAtLeast(0L)
            UsageResult(used, limit)
        }
    }

    fun translateSingle(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslateResult {
        if (authKey.isBlank()) return TranslateResult(null, 0L, "DeepL auth key is empty")
        if (text.isBlank()) return TranslateResult("", 0L, null)
        return try {
            val payload = mapOf(
                "text" to listOf(text),
                "source_lang" to sourceLang,
                "target_lang" to targetLang,
                "show_billed_characters" to true,
            )
            val responseBody = execute("POST", "/v2/translate", mapper.writeValueAsString(payload))
            val root = mapper.readTree(responseBody)
            val first = root.path("translations").firstOrNull()
            val translated = first?.path("text")?.asText()?.trim().orEmpty()
            if (translated.isBlank()) {
                TranslateResult(null, 0L, "Empty translation response")
            } else {
                val billed = first?.path("billed_characters")?.asLong(0L)?.coerceAtLeast(0L) ?: 0L
                TranslateResult(translated, billed, null)
            }
        } catch (e: Exception) {
            TranslateResult(null, 0L, e.message ?: e.toString())
        }
    }

    private fun execute(method: String, path: String, jsonBody: String?): String {
        val uri = URI.create(resolveBaseUrl() + path)
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "DeepL-Auth-Key $authKey")
        val request = when (method) {
            "POST" -> builder
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        jsonBody ?: "{}",
                        StandardCharsets.UTF_8,
                    ),
                )
                .build()
            else -> builder.GET().build()
        }
        val response = PluginHttpApiClient.send(request)
        val body = response.body().orEmpty()
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorMessage(body) ?: "HTTP ${response.statusCode()}"
            throw IOException(msg)
        }
        if (body.isBlank()) throw IOException("Empty response body")
        return body
    }

    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val root = mapper.readTree(body)
            root.path("message").asText().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun resolveBaseUrl(): String =
        if (authKey.trim().endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"
}

private fun JsonNode.firstOrNull(): JsonNode? =
    if (isArray && size() > 0) get(0) else null
