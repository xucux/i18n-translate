package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.domain.SupportedLanguage

/**
 * 「添加到目标」弹窗中的选项：更新已有 [TargetEntry] 或使用枚举中**尚未出现在目标配置**的语种新建一行。
 */
sealed class TargetFileBindOption {
    /** 将选中文件路径写回该已有目标行。 */
    data class UpdateExistingRow(val row: TargetEntry) : TargetFileBindOption()

    /** 使用尚未被任一目标行占用的枚举语种新增一条 [TargetEntry]。 */
    data class NewLanguageFromEnum(val language: SupportedLanguage) : TargetFileBindOption()

    companion object {
        /**
         * 构建「添加到目标」下拉项：先列出已有行，再列出 [SupportedLanguage.entries] 中未被 [targets] 占用的语种。
         */
        fun buildOptions(targets: List<TargetEntry>): List<TargetFileBindOption> {
            val unusedLanguages = SupportedLanguage.entries.filter { lang ->
                targets.none { it.language == lang }
            }
            return targets.map { UpdateExistingRow(it) } +
                unusedLanguages.map { NewLanguageFromEnum(it) }
        }
    }
}
