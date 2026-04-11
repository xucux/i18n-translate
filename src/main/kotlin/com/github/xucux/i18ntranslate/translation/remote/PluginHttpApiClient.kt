package com.github.xucux.i18ntranslate.translation.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 插件内远程翻译 API 共用的 OkHttp 客户端（含统一调试日志拦截器）。 */
internal object PluginHttpApiClient {

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(OkHttpPluginLoggingInterceptor())
            .build()

    data class HttpTextResponse(val statusCode: Int, val body: String)

    fun send(request: Request): HttpTextResponse {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return HttpTextResponse(response.code, text)
        }
    }
}
