package com.github.xucux.i18ntranslate.config

import com.github.xucux.i18ntranslate.config.PluginStoragePaths.globalPropertiesPath
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

/**
 * 全局（跨项目）配置：引擎、文件编码、目标写入模式、云凭证。
 * 序列化至 [PluginStoragePaths.globalPropertiesPath]。
 */
data class GlobalPluginState(
    /** 设置页及其它 UI 使用的文案语言（与 IDE 界面语言独立）。 */
    var uiLanguage: PluginUiLanguage = PluginUiLanguage.ENGLISH,
    /** 当前选用的云翻译产品线。 */
    var translateEngine: TranslateEngine = TranslateEngine.ALIYUN,
    /** 读写消息文件的字符集名称（如 UTF-8）。 */
    var charsetName: String = "UTF-8",
    /** 为 true 时 [charsetName] 可为用户自定义编码，不再限于 preset 下拉。 */
    var customCharset: Boolean = false,
    /** 向目标文件写入翻译结果时的冲突策略。 */
    var writeMode: FileWriteMode = FileWriteMode.OVERWRITE,
    /** 阿里云 AccessKeyId。 */
    var aliyunAccessKeyId: String = "",
    /** 阿里云 AccessKeySecret。 */
    var aliyunAccessKeySecret: String = "",
    /** 腾讯云 SecretId。 */
    var tencentSecretId: String = "",
    /** 腾讯云 SecretKey。 */
    var tencentSecretKey: String = "",
) {
    /** 读写资源文件时使用的字符集名称（与 [charsetName] 一致）。 */
    fun resolveCharset(): String = charsetName

    companion object {
        private const val K_UI_LANGUAGE = "ui.language"

        private const val K_ENGINE = "translate.engine"
        private const val K_CHARSET = "file.charset"
        private const val K_CUSTOM_CHARSET = "file.customCharset"
        private const val K_WRITE = "file.writeMode"
        private const val K_AK_ID = "aliyun.accessKeyId"
        private const val K_AK_SECRET = "aliyun.accessKeySecret"
        private const val K_TX_ID = "tencent.secretId"
        private const val K_TX_KEY = "tencent.secretKey"

        /** 从磁盘加载；文件不存在则返回默认实例。 */
        fun load(): GlobalPluginState {
            val path = globalPropertiesPath()
            if (!Files.isRegularFile(path)) return GlobalPluginState()
            val props = Properties()
            Files.newInputStream(path).use { ins ->
                InputStreamReader(ins, StandardCharsets.UTF_8).use { reader ->
                    props.load(reader)
                }
            }
            return GlobalPluginState(
                uiLanguage = props.getProperty(K_UI_LANGUAGE)?.let { runCatching { PluginUiLanguage.valueOf(it) }.getOrNull() }
                    ?: PluginUiLanguage.ENGLISH,
                translateEngine = props.getProperty(K_ENGINE)?.let { runCatching { TranslateEngine.valueOf(it) }.getOrNull() }
                    ?: TranslateEngine.ALIYUN,
                charsetName = props.getProperty(K_CHARSET, "UTF-8"),
                customCharset = props.getProperty(K_CUSTOM_CHARSET, "false").toBoolean(),
                writeMode = props.getProperty(K_WRITE)?.let { runCatching { FileWriteMode.valueOf(it) }.getOrNull() }
                    ?: FileWriteMode.OVERWRITE,
                aliyunAccessKeyId = props.getProperty(K_AK_ID, ""),
                aliyunAccessKeySecret = props.getProperty(K_AK_SECRET, ""),
                tencentSecretId = props.getProperty(K_TX_ID, ""),
                tencentSecretKey = props.getProperty(K_TX_KEY, ""),
            )
        }

        /** 覆盖写入全局 properties（UTF-8）。 */
        fun save(state: GlobalPluginState) {
            val props = Properties()
            props.setProperty(K_UI_LANGUAGE, state.uiLanguage.name)
            props.setProperty(K_ENGINE, state.translateEngine.name)
            props.setProperty(K_CHARSET, state.charsetName)
            props.setProperty(K_CUSTOM_CHARSET, state.customCharset.toString())
            props.setProperty(K_WRITE, state.writeMode.name)
            props.setProperty(K_AK_ID, state.aliyunAccessKeyId)
            props.setProperty(K_AK_SECRET, state.aliyunAccessKeySecret)
            props.setProperty(K_TX_ID, state.tencentSecretId)
            props.setProperty(K_TX_KEY, state.tencentSecretKey)
            Files.createDirectories(globalPropertiesPath().parent)
            Files.newOutputStream(globalPropertiesPath()).use { out ->
                OutputStreamWriter(out, StandardCharsets.UTF_8).use { w ->
                    props.store(w, "I18N Translate — global")
                }
            }
        }
    }
}
