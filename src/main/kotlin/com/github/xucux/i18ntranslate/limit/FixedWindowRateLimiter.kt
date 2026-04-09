package com.github.xucux.i18ntranslate.limit

/**
 * 固定时间窗口限流：每个窗口 [windowMillis] 内最多 [maxPermits] 次放行。
 * 用作阿里云（50 QPS）、腾讯云（5 QPS）等调用前节流。
 *
 * @param maxPermits 单窗口内允许的最大请求次数
 * @param windowMillis 窗口长度（毫秒），默认 1000 即按秒计
 */
class FixedWindowRateLimiter(
    private val maxPermits: Int,
    private val windowMillis: Long = 1000L,
) {
    @Volatile
    private var windowStart: Long = System.currentTimeMillis()

    @Volatile
    private var count: Int = 0

    private val lock = Any()

    /** 阻塞直至当前窗口内仍有余量，用于调用云 API 前串行限流。 */
    fun acquireBlocking() {
        while (true) {
            val waitMs = synchronized(lock) {
                val now = System.currentTimeMillis()
                if (now - windowStart >= windowMillis) {
                    windowStart = now
                    count = 0
                }
                if (count < maxPermits) {
                    count++
                    return
                }
                windowMillis - (now - windowStart)
            }
            val sleep = waitMs.coerceAtLeast(1L)
            Thread.sleep(sleep)
        }
    }
}
