package com.github.xucux.i18ntranslate.view.dialog

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JTextField

/** 将磁盘上的资源文件绑定到配置中的源路径或某一目标路径（需求 3.4）。 */
enum class AssignI18nFilePathMode {
    SOURCE,
    TARGET,
}

/**
 * 弹窗：只读路径 + 第二栏选择，确认后再写入。
 *
 * - **SOURCE**：[sourceLanguageOptions] 为完整 [SupportedLanguage] 枚举；可由 [preferredSourceLanguage] 预选。
 * - **TARGET**：[targetBindOptions] 为 [TargetFileBindOption.buildOptions]；可由 [preferredTargetLanguage] 预选。
 * 无法从文件名识别语种时二者为 `null`，下拉无默认选中。
 */
class AssignI18nFilePathDialog(
    project: Project?,
    private val mode: AssignI18nFilePathMode,
    filePath: String,
    private val sourceLanguageOptions: List<SupportedLanguage> = emptyList(),
    private val targetBindOptions: List<TargetFileBindOption> = emptyList(),
    private val preferredSourceLanguage: SupportedLanguage? = null,
    private val preferredTargetLanguage: SupportedLanguage? = null,
    /** 当前文件已是配置中的目标文件时：使用「替换文件到目标」标题（需求 3.4）。 */
    private val replaceTargetFlow: Boolean = false,
) : DialogWrapper(project) {

    private val pathField = JTextField(filePath).apply { isEditable = false }

    private val sourceLangCombo = languageCombo(sourceLanguageOptions)

    private val bindCombo = ComboBox(targetBindOptions.toTypedArray()).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                v: Any?,
                i: Int,
                sel: Boolean,
                f: Boolean,
            ) = super.getListCellRendererComponent(list, v, i, sel, f).apply {
                text = when (val o = v as? TargetFileBindOption) {
                    is TargetFileBindOption.UpdateExistingRow ->
                        "${o.row.language.displayLabelWithCode} → ${o.row.filePath}"
                    is TargetFileBindOption.NewLanguageFromEnum ->
                        "${o.language.displayLabelWithCode} — ${I18nTranslateBundle.message("dialog.assign.target.option.new.enum")}"
                    else -> ""
                }
            }
        }
    }

    init {
        when (mode) {
            AssignI18nFilePathMode.SOURCE -> applySourcePreferred()
            AssignI18nFilePathMode.TARGET -> applyTargetPreferred()
        }
        title = when (mode) {
            AssignI18nFilePathMode.SOURCE -> I18nTranslateBundle.message("dialog.assign.source.title")
            AssignI18nFilePathMode.TARGET ->
                if (replaceTargetFlow) I18nTranslateBundle.message("dialog.assign.replace.target.title")
                else I18nTranslateBundle.message("dialog.assign.target.title")
        }
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        init()
    }

    private fun applySourcePreferred() {
        if (sourceLanguageOptions.isEmpty()) return
        val idx = preferredSourceLanguage?.let { pref ->
            sourceLanguageOptions.indexOf(pref).takeIf { it >= 0 }
        } ?: -1
        sourceLangCombo.selectedIndex = idx
    }

    private fun applyTargetPreferred() {
        if (targetBindOptions.isEmpty()) return
        val idx = preferredTargetLanguage?.let { pref ->
            targetBindOptions.indexOfFirst { opt ->
                when (opt) {
                    is TargetFileBindOption.UpdateExistingRow -> opt.row.language == pref
                    is TargetFileBindOption.NewLanguageFromEnum -> opt.language == pref
                }
            }.takeIf { it >= 0 }
        } ?: -1
        bindCombo.selectedIndex = idx
    }

    private fun languageCombo(options: List<SupportedLanguage>) =
        ComboBox(options.toTypedArray()).apply {
            renderer = object : javax.swing.DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?,
                    v: Any?,
                    i: Int,
                    sel: Boolean,
                    f: Boolean,
                ) = super.getListCellRendererComponent(list, v, i, sel, f).apply {
                    val lang = v as? SupportedLanguage
                    text = lang?.displayLabelWithCode ?: ""
                }
            }
        }

    override fun doValidate(): ValidationInfo? {
        return when (mode) {
            AssignI18nFilePathMode.SOURCE ->
                if (sourceLangCombo.selectedItem == null) {
                    ValidationInfo(
                        I18nTranslateBundle.message("error.assign.language.required"),
                        sourceLangCombo,
                    )
                } else {
                    null
                }
            AssignI18nFilePathMode.TARGET ->
                if (bindCombo.selectedItem == null) {
                    ValidationInfo(
                        I18nTranslateBundle.message("error.assign.language.required"),
                        bindCombo,
                    )
                } else {
                    null
                }
        }
    }

    /** 【添加到源】确认后的语种（完整枚举）。 */
    fun selectedSourceLanguage(): SupportedLanguage? =
        if (mode == AssignI18nFilePathMode.SOURCE) sourceLangCombo.selectedItem as? SupportedLanguage else null

    /** 【添加到目标】确认后的绑定选项。 */
    fun selectedTargetBindOption(): TargetFileBindOption? =
        if (mode == AssignI18nFilePathMode.TARGET) bindCombo.selectedItem as? TargetFileBindOption else null

    override fun createCenterPanel(): JComponent {
        val pathLabel = when (mode) {
            AssignI18nFilePathMode.SOURCE -> I18nTranslateBundle.message("dialog.source.path")
            AssignI18nFilePathMode.TARGET -> I18nTranslateBundle.message("settings.target.path")
        }
        val langLabel = when (mode) {
            AssignI18nFilePathMode.SOURCE -> I18nTranslateBundle.message("dialog.assign.language.enum")
            AssignI18nFilePathMode.TARGET -> I18nTranslateBundle.message("dialog.assign.language.enum.exclude.used")
        }
        val langField: JComponent = when (mode) {
            AssignI18nFilePathMode.SOURCE -> sourceLangCombo
            AssignI18nFilePathMode.TARGET -> bindCombo
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(pathLabel, pathField)
            .addLabeledComponent(langLabel, langField)
            .panel
    }
}
