package com.github.xucux.i18ntranslate.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** 将用户配置的编码名转为 [Charset]；非法名称时回落 UTF-8。 */
object CharsetUtil {
    /**
     * @param name 编码名，如 `UTF-8`、`GBK`。
     */
    fun resolve(name: String): Charset =
        runCatching { Charset.forName(name) }.getOrElse { StandardCharsets.UTF_8 }
}
