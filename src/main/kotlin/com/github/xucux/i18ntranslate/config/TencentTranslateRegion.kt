package com.github.xucux.i18ntranslate.config

/**
 * 腾讯云机器翻译（TMT）API 的 `X-TC-Region` 可选地域，与控制台地域列表一致。
 */
enum class TencentTranslateRegion(
    /** 接口入参地域代码，如 `ap-guangzhou`。 */
    val code: String,
    private val labelZh: String,
    private val labelEn: String,
) {
    AP_BANGKOK("ap-bangkok", "亚太东南（曼谷）", "Asia Pacific (Bangkok)"),
    AP_BEIJING("ap-beijing", "华北地区（北京）", "North China (Beijing)"),
    AP_CHENGDU("ap-chengdu", "西南地区（成都）", "Southwest China (Chengdu)"),
    AP_CHONGQING("ap-chongqing", "西南地区（重庆）", "Southwest China (Chongqing)"),
    AP_GUANGZHOU("ap-guangzhou", "华南地区（广州）", "South China (Guangzhou)"),
    AP_HONGKONG("ap-hongkong", "港澳台地区（中国香港）", "Hong Kong, China"),
    AP_SEOUL("ap-seoul", "亚太东北（首尔）", "Asia Pacific (Seoul)"),
    AP_SHANGHAI("ap-shanghai", "华东地区（上海）", "East China (Shanghai)"),
    AP_SHANGHAI_FSI("ap-shanghai-fsi", "华东地区（上海金融）", "East China — Shanghai Finance"),
    AP_SHENZHEN_FSI("ap-shenzhen-fsi", "华南地区（深圳金融）", "South China — Shenzhen Finance"),
    AP_SINGAPORE("ap-singapore", "亚太东南（新加坡）", "Asia Pacific (Singapore)"),
    AP_TOKYO("ap-tokyo", "亚太东北（东京）", "Asia Pacific (Tokyo)"),
    EU_FRANKFURT("eu-frankfurt", "欧洲地区（法兰克福）", "Europe (Frankfurt)"),
    NA_ASHBURN("na-ashburn", "美国东部（弗吉尼亚）", "US East (Virginia)"),
    NA_SILICONVALLEY("na-siliconvalley", "美国西部（硅谷）", "US West (Silicon Valley)"),
    ;

    /** 设置页下拉展示：`中文 / English`（与引擎下拉风格一致）。 */
    fun displayComboLabel(): String = "$labelZh / $labelEn"

    companion object {
        const val DEFAULT_CODE: String = "ap-guangzhou"

        fun fromCodeOrDefault(code: String?): TencentTranslateRegion {
            if (code.isNullOrBlank()) return AP_GUANGZHOU
            return values().firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: AP_GUANGZHOU
        }
    }
}
