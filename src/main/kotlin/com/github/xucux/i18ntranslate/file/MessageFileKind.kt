package com.github.xucux.i18ntranslate.file

import java.nio.file.Path
import kotlin.io.path.extension

/** 由文件扩展名推断的扁平消息资源类型（与 [FlatMessageFileIo] 配合）。 */
enum class MessageFileKind {
    /** `.json` 单层对象映射。 */
    JSON,
    /** `.properties` Java 资源束。 */
    PROPERTIES,
    /** `.yaml` / `.yml` 扁平键值。 */
    YAML,
    ;

    companion object {
        /** 据扩展名推断消息文件类型；不支持的后缀返回 `null`。 */
        fun fromPath(path: Path): MessageFileKind? {
            return when (path.extension.lowercase()) {
                "json" -> JSON
                "properties" -> PROPERTIES
                "yaml", "yml" -> YAML
                else -> null
            }
        }
    }
}
