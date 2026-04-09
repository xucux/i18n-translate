package com.github.xucux.i18ntranslate.config

import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

/**
 * 翻译引擎调用统计的内存缓存：启动时从用户目录 `stats.properties` 载入，
 * 由 [I18nStatsService] 间隔约 10 分钟落盘，并在服务释放（含 IDE 退出）时再写一次。
 */
class I18nStatsMemoryCache {
    /** 翻译 API 调用总次数（含成功与失败）。 */
    val totalCalls = AtomicLong(0)
    /** 返回译文的调用次数。 */
    val successCalls = AtomicLong(0)
    /** 抛出异常或业务失败的调用次数。 */
    val failedCalls = AtomicLong(0)
    /** 成功请求返回的 WordCount（阿里）或 UsedAmount（腾讯）之累计（展示为「已翻译单词总数」）。 */
    val translatedWordsTotal = AtomicLong(0)

    /** 在内存中累加一次调用；成功时额外增加 [billedWordsOnSuccess]。 */
    fun record(success: Boolean, billedWordsOnSuccess: Long) {
        totalCalls.incrementAndGet()
        if (success) {
            successCalls.incrementAndGet()
            val n = billedWordsOnSuccess.coerceAtLeast(0L)
            if (n > 0) translatedWordsTotal.addAndGet(n)
        } else {
            failedCalls.incrementAndGet()
        }
    }

    /** 从 [PluginStoragePaths.statsPropertiesPath] 恢复计数；文件不存在则保持默认零值。 */
    fun loadFromDisk() {
        val path = PluginStoragePaths.statsPropertiesPath()
        if (!Files.isRegularFile(path)) return
        val props = Properties()
        Files.newInputStream(path).use { ins ->
            InputStreamReader(ins, StandardCharsets.UTF_8).use { props.load(it) }
        }
        totalCalls.set(props.getProperty("total", "0").toLongOrNull() ?: 0L)
        successCalls.set(props.getProperty("success", "0").toLongOrNull() ?: 0L)
        failedCalls.set(props.getProperty("failed", "0").toLongOrNull() ?: 0L)
        translatedWordsTotal.set(props.getProperty("wordsTotal", "0").toLongOrNull() ?: 0L)
    }

    /** 将总调用、成功、失败与已翻译单词累计全部清零。 */
    @Synchronized
    fun resetAllCounts() {
        totalCalls.set(0)
        successCalls.set(0)
        failedCalls.set(0)
        translatedWordsTotal.set(0)
    }

    /** 将内存中的四项计数原子写入 stats 文件（UTF-8）。 */
    @Synchronized
    fun flushToDisk() {
        val props = Properties()
        props.setProperty("total", totalCalls.get().toString())
        props.setProperty("success", successCalls.get().toString())
        props.setProperty("failed", failedCalls.get().toString())
        props.setProperty("wordsTotal", translatedWordsTotal.get().toString())
        Files.createDirectories(PluginStoragePaths.statsPropertiesPath().parent)
        Files.newOutputStream(PluginStoragePaths.statsPropertiesPath()).use { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { w ->
                props.store(w, "I18N Translate — stats")
            }
        }
    }
}
