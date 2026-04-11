package com.github.xucux.i18ntranslate.translation.remote

import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal object PluginHttpApiClient {
    private val log = Logger.getInstance(PluginHttpApiClient::class.java)
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private const val LOG_PREFIX = "I18N::"
    private const val BODY_PREVIEW_MAX = 16_384

    private fun httpDebugEnabled(): Boolean =
        runCatching {
            ApplicationManager.getApplication()
                .getService(GlobalPluginConfigService::class.java)
                .getState()
                .httpDebugLogging
        }.getOrDefault(false)

    private fun redactHeaderValue(name: String, value: String): String =
        when (name.lowercase()) {
            "authorization", "deepl-auth-key" -> "***REDACTED***"
            else -> value
        }

    private fun formatRequestForLog(request: HttpRequest): String = buildString {
        append(request.method()).append(' ').append(request.uri()).append('\n')
        request.headers().map().forEach { (headerName, values) ->
            values.forEach { v ->
                append("  ").append(headerName).append(": ")
                append(redactHeaderValue(headerName, v)).append('\n')
            }
        }
    }.trimEnd()

    fun send(request: HttpRequest): HttpResponse<String> {
        val debug = httpDebugEnabled()
        if (debug) {
            log.info("$LOG_PREFIX REQUEST\n${formatRequestForLog(request)}")
        }
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            if (debug) {
                log.warn("$LOG_PREFIX transport error: ${e.message}", e)
            }
            throw e
        }
        if (debug) {
            val body = response.body().orEmpty()
            val preview =
                if (body.length > BODY_PREVIEW_MAX) {
                    body.take(BODY_PREVIEW_MAX) + "…(truncated, ${body.length} chars total)"
                } else {
                    body
                }
            log.info("$LOG_PREFIX RESPONSE status=${response.statusCode()}\n$preview")
        }
        return response
    }
}
