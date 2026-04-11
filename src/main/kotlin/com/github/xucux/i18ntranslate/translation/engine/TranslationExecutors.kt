package com.github.xucux.i18ntranslate.translation.engine

import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.github.xucux.i18ntranslate.config.GlobalPluginState
import com.github.xucux.i18ntranslate.config.I18nStatsService
import com.github.xucux.i18ntranslate.config.TranslateEngine
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.translation.api.TranslateOutcome
import com.github.xucux.i18ntranslate.translation.api.TranslationExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

/**
 * 根据全局配置构造具体 [TranslationExecutor]，并统一在翻译后上报 [I18nStatsService]。
 */
object TranslationExecutors {
    private val logger = Logger.getInstance(TranslationExecutors::class.java)

    /** 从 Application 级 [GlobalPluginConfigService] 读取当前配置并创建执行器。 */
    fun createFromApplicationService(): TranslationExecutor {
        val svc = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java)
        val state = svc.getState()
        logger.info("Creating translation executor from application service: engine=${state.translateEngine.name}")
        return create(state)
    }

    /** 按 [GlobalPluginState.translateEngine] 选择阿里云或腾讯实现。 */
    fun create(state: GlobalPluginState): TranslationExecutor = when (state.translateEngine) {
        TranslateEngine.ALIYUN -> {
            logger.info("Using Aliyun translation executor")
            AliyunTranslationExecutor(
                state.aliyunAccessKeyId,
                state.aliyunAccessKeySecret,
            )
        }
        TranslateEngine.TENCENT -> {
            logger.info("Using Tencent translation executor")
            TencentTranslationExecutor(
                state.tencentSecretId,
                state.tencentSecretKey,
                state.tencentRegion,
            )
        }
        TranslateEngine.DEEPL -> {
            logger.info("Using DeepL translation executor")
            DeepLTranslationExecutor(state.deepLAuthKey)
        }
    }

    /**
     * 调用 [executor] 翻译，并向 [stats] 记录一次成功/失败及 [TranslateOutcome.billedWords]（[stats] 为 `null` 则不计）。
     */
    fun translateWithStats(
        executor: TranslationExecutor,
        stats: I18nStatsService?,
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): TranslateOutcome {
        logger.debug(
            "Translating text with stats: source=${source.name}, target=${target.name}, textLength=${text.length}",
        )
        val r = executor.translate(text, source, target)
        val words = if (r.ok) r.billedWords?.coerceAtLeast(0L) ?: 0L else 0L
        stats?.record(r.ok, words)
        if (r.ok) {
            logger.info("Translation success: source=${source.name}, target=${target.name}, billedWords=$words")
        } else {
            logger.warn("Translation failed: source=${source.name}, target=${target.name}, error=${r.errorMessage}")
        }
        return r
    }
}
