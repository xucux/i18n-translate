package com.github.xucux.i18ntranslate.domain

/**
 * 从 `properties` / `json` / `yaml` / `yml` 资源文件名中猜测 [SupportedLanguage]（如 `messages_vi.properties`、`app.zh-CN.json`）。
 */
object I18nResourceFileNameLanguageGuess {

    private val SUPPORTED_EXT = setOf("properties", "json", "yaml", "yml")

    /**
     * @param fileName 含扩展名的文件名（不含路径），如 `strings_zh_CN.properties`
     * @return 无法从常见后缀/区域片段识别时返回 `null`
     */
    fun guessFromFileName(fileName: String): SupportedLanguage? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED_EXT) return null
        val stem = fileName.substringBeforeLast('.')
        if (stem.isBlank()) return null
        val tokens = stem.replace('-', '_').split('_', '.').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        if (tokens.size >= 2) {
            resolveTwoPart(tokens[tokens.size - 2], tokens[tokens.size - 1])?.let { return it }
        }
        resolveSingle(tokens.last())?.let { return it }
        if (tokens.size >= 2) {
            resolveSingle(tokens[tokens.size - 2])?.let { return it }
        }
        return null
    }

    private fun resolveTwoPart(a: String, b: String): SupportedLanguage? {
        val al = a.lowercase()
        val bl = b.lowercase()
        return when {
            al == "zh" && bl in setOf("cn", "hans", "sg") -> SupportedLanguage.CHINESE
            al == "zh" && bl in setOf("tw", "hk", "hant", "mo") -> SupportedLanguage.CHINESE_TRADITIONAL
            al == "pt" && bl == "br" -> SupportedLanguage.PORTUGUESE
            al == "en" && bl in setOf("us", "gb", "uk", "au") -> SupportedLanguage.ENGLISH
            else -> {
                val hyphen = "${al}-${bl.uppercase()}"
                SupportedLanguage.fromTencentCode(hyphen)
                    ?: SupportedLanguage.fromAliyunCode("${al}_$bl")
                    ?: SupportedLanguage.fromAliyunCode("$al-$bl")
                    ?: SupportedLanguage.fromAliyunCode(hyphen)
            }
        }
    }

    private fun resolveSingle(t: String): SupportedLanguage? {
        val s = t.lowercase()
        if (s.length !in 2..7) return null
        if (s in SUPPORTED_EXT) return null
        return when (s) {
            "zh" -> SupportedLanguage.CHINESE
            else ->
                SupportedLanguage.fromAliyunCode(s)
                    ?: SupportedLanguage.fromTencentCode(s)
                    ?: SupportedLanguage.fromTencentCode(s.replace('_', '-'))
        }
    }
}
