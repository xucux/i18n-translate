package com.github.xucux.i18ntranslate.ui

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * 「整文件 key 译到某一目标」：展示 key 总数、源路径与目标下拉（需求 3.3）。
 *
 * @param keyCount 源文件中 key 条目数量（只读展示）
 * @param targetOptions 用户可选择写入翻译结果的目标列表
 */
class TranslateFileKeysDialog(
    val keyCount: Int,
    sourcePath: String,
    val targetOptions: List<TargetEntry>,
) : DialogWrapper(null) {

    private val countField = JTextField(keyCount.toString()).apply { isEditable = false }
    private val sourceField = JTextField(sourcePath).apply { isEditable = false }
    private val targetCombo = ComboBox(targetOptions.toTypedArray()).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                v: Any?,
                i: Int,
                sel: Boolean,
                f: Boolean,
            ) = super.getListCellRendererComponent(list, v, i, sel, f).apply {
                val t = v as? TargetEntry
                text = t?.let { "${it.language.displayLabelWithCode} → ${it.filePath}" } ?: ""
            }
        }
        if (targetOptions.isNotEmpty()) selectedIndex = 0
    }

    init {
        title = I18nTranslateBundle.message("dialog.translate.file.title")
        setOKButtonText(I18nTranslateBundle.message("dialog.ok"))
        setCancelButtonText(I18nTranslateBundle.message("dialog.cancel"))
        init()
    }

    /** 用户确认后选中的写入目标。 */
    fun selectedTarget(): TargetEntry? = targetCombo.selectedItem as? TargetEntry

    /** 表单项：key 数、源路径、目标语种下拉。 */
    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent(I18nTranslateBundle.message("dialog.all.keys.count"), countField)
            .addLabeledComponent(I18nTranslateBundle.message("dialog.source.path"), sourceField)
            .addLabeledComponent(I18nTranslateBundle.message("dialog.target.lang"), targetCombo)
            .panel
}
