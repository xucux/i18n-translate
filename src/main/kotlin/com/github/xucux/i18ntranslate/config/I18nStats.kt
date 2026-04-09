package com.github.xucux.i18ntranslate.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicLong

/**
 * 翻译 API 调用统计（总次数 / 成功 / 失败 / 已翻译单词累计）。
 * 启动时经 [I18nStatsMemoryCache] 读盘；约每 10 分钟写盘一次；释放时再写一次（含 IDE 关闭）。
 */
@Service(Service.Level.APP)
class I18nStatsService : Disposable {

    private val cache = I18nStatsMemoryCache()

    /** 与 [I18nStatsMemoryCache.totalCalls] 同源，供 UI 绑定。 */
    val totalCalls: AtomicLong get() = cache.totalCalls
    /** 与 [I18nStatsMemoryCache.successCalls] 同源。 */
    val successCalls: AtomicLong get() = cache.successCalls
    /** 与 [I18nStatsMemoryCache.failedCalls] 同源。 */
    val failedCalls: AtomicLong get() = cache.failedCalls
    /** 与 [I18nStatsMemoryCache.translatedWordsTotal] 同源。 */
    val translatedWordsTotal: AtomicLong get() = cache.translatedWordsTotal

    init {
        cache.loadFromDisk()
        Disposer.register(this, Disposable { cache.flushToDisk() })
        scheduleFlush()
    }

    /** 每次调用云翻译 API 后计入一次；仅在 [success] 为 true 时累加 [billedWordsOnSuccess]（阿里 WordCount / 腾讯 UsedAmount）。 */
    fun record(success: Boolean, billedWordsOnSuccess: Long = 0L) {
        cache.record(success, billedWordsOnSuccess)
    }

    /** 在后台线程中约每 10 分钟 [flushToDisk]，并递归调度直至服务释放。 */
    private fun scheduleFlush() {
        val app = ApplicationManager.getApplication()
        if (app.isDisposed) return
        app.executeOnPooledThread {
            try {
                Thread.sleep(600_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (!Disposer.isDisposed(this)) {
                cache.flushToDisk()
                scheduleFlush()
            }
        }
    }

    /** 将当前内存计数写入磁盘（线程安全）。 */
    fun flushToDisk() {
        cache.flushToDisk()
    }

    /** 清零调用次数与已翻译单词等全部统计，并立即落盘。 */
    fun resetAllCountsAndPersist() {
        cache.resetAllCounts()
        cache.flushToDisk()
    }

    /** Application 释放时最终落盘。 */
    override fun dispose() {
        cache.flushToDisk()
    }
}
