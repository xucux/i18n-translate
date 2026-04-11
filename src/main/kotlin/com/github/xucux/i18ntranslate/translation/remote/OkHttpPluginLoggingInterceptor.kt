package com.github.xucux.i18ntranslate.translation.remote

import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException

/**
 * 统一 HTTP 调试日志：请求与响应使用 [Logger.info]；是否输出由全局配置项 `httpDebugLogging` 控制。
 */
internal class OkHttpPluginLoggingInterceptor : Interceptor {

    private val log = Logger.getInstance(OkHttpPluginLoggingInterceptor::class.java)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!httpDebugEnabled()) {
            return chain.proceed(chain.request())
        }

        val original = chain.request()
        val (requestToSend, bodyPreview) = duplicateBodyForLogging(original)
        logRequest(requestToSend, bodyPreview)

        val response =
            try {
                chain.proceed(requestToSend)
            } catch (e: IOException) {
                log.warn("$LOG_PREFIX transport error: ${e.message}", e)
                throw e
            } catch (e: Exception) {
                log.warn("$LOG_PREFIX transport error: ${e.message}", e)
                throw e
            }

        return logResponse(response)
    }

    private fun duplicateBodyForLogging(request: Request): Pair<Request, String?> {
        val body = request.body ?: return request to null
        val buffer = Buffer()
        body.writeTo(buffer)
        val raw = buffer.clone().readUtf8()
        val preview =
            if (raw.length > BODY_PREVIEW_MAX) {
                raw.take(BODY_PREVIEW_MAX) + "…(truncated, ${raw.length} chars total)"
            } else {
                raw
            }
        val newBody = buffer.readByteArray().toRequestBody(body.contentType())
        val newRequest = request.newBuilder().method(request.method, newBody).build()
        return newRequest to preview
    }

    private fun logRequest(request: Request, bodyPreview: String?) {
        val sb = StringBuilder()
        sb.append(LOG_PREFIX).append(" REQUEST\n")
        sb.append(request.method).append(' ').append(request.url).append('\n')
        appendHeaders(sb, request.headers)
        if (!bodyPreview.isNullOrEmpty()) {
            sb.append('\n').append(bodyPreview)
        }
        log.info(sb.toString().trimEnd())
    }

    private fun logResponse(response: Response): Response {
        if (!responsePromisesBody(response)) {
            log.info("$LOG_PREFIX RESPONSE status=${response.code} (no body)")
            return response
        }
        val body = response.body ?: return response
        return try {
            val peeked = response.peekBody(BODY_PREVIEW_MAX.toLong())
            val text = peeked.string()
            val logged =
                if (text.length >= BODY_PREVIEW_MAX) {
                    "$text…(peek truncated at $BODY_PREVIEW_MAX bytes)"
                } else {
                    text
                }
            log.info("$LOG_PREFIX RESPONSE status=${response.code}\n$logged")
            response
        } catch (e: Exception) {
            log.warn("$LOG_PREFIX failed to log response body: ${e.message}", e)
            response
        }
    }

    private fun responsePromisesBody(response: Response): Boolean {
        if (response.body == null) return false
        if (response.request.method == "HEAD") return false
        val code = response.code
        if (code == 204 || code == 205) return false
        return true
    }

    private fun appendHeaders(sb: StringBuilder, headers: Headers) {
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = headers.value(i)
            sb.append("  ")
                .append(name)
                .append(": ")
                .append(redactHeaderValue(name, value))
                .append('\n')
        }
    }

    companion object {
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
    }
}
