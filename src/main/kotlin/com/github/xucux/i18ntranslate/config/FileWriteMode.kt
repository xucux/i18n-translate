package com.github.xucux.i18ntranslate.config

/** 向目标语言文件写入翻译时的策略（覆盖或跳过已有 key）。 */
enum class FileWriteMode {
    /** 覆盖目标文件中已存在的 key */
    OVERWRITE,

    /** 跳过已存在的 key，仅写入缺失项 */
    SKIP,
}
