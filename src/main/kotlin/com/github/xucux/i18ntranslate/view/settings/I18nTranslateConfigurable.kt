package com.github.xucux.i18ntranslate.view.settings

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.FileWriteMode
import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.github.xucux.i18ntranslate.config.GlobalPluginState
import com.github.xucux.i18ntranslate.config.I18nStatsService
import com.github.xucux.i18ntranslate.config.PluginUiLanguage
import com.github.xucux.i18ntranslate.config.ProjectI18nConfigService
import com.github.xucux.i18ntranslate.config.ProjectI18nState
import com.github.xucux.i18ntranslate.config.TargetEntry
import com.github.xucux.i18ntranslate.config.TranslateEngine
import com.github.xucux.i18ntranslate.domain.SupportedLanguage
import com.github.xucux.i18ntranslate.view.dialog.EngineCredentialsDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Settings → Other Settings → I18N Translate：全局选项、统计只读、本项目源/目标路径与语种。
 */
class I18nTranslateConfigurable(private val project: Project) : Configurable, Disposable {

    /** 全局选项编辑基准（含凭证弹窗未 Apply 前的内存修改）。 */
    private var globalSnapshot: GlobalPluginState = GlobalPluginState.load()
    /** 本项目源/目标路径与语种的编辑基准。 */
    private var projectSnapshot: ProjectI18nState = ProjectI18nState.load(project)

    /** 翻译引擎：阿里云 / 腾讯云 / DeepL，对应 [GlobalPluginState.translateEngine]。 */
    private val engineCombo = ComboBox(arrayOf(TranslateEngine.ALIYUN, TranslateEngine.TENCENT, TranslateEngine.DEEPL)).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                text = (value as? TranslateEngine)?.let { "${it.displayZh()} / ${it.displayEn()}" } ?: ""
            }
        }
        setMinimumAndPreferredWidth(JBUI.scale(300))
    }

    /** 文件编码下拉框的预设项（与 [charsetCombo] 联动）。 */
    private val charsetPresets = arrayOf("UTF-8", "GBK", "ISO-8859-1", "UTF-16")

    /** 消息文件读写字符集（未勾选自定义时写入 [GlobalPluginState.charsetName]）。 */
    private val charsetCombo = ComboBox(charsetPresets)

    /** 勾选后允许手动输入任意编码名，并启用 [customCharsetField]。 */
    private val customCharsetCheck = JBCheckBox()

    /** 自定义字符集名称输入框（与 [GlobalPluginState.customCharset] 配合）。 */
    private val customCharsetField = com.intellij.ui.components.JBTextField()

    /** 目标文件写入策略：覆盖已有 key。 */
    private val overwriteRadio = JBRadioButton()

    /** 目标文件写入策略：已存在 key 则跳过翻译与写入。 */
    private val skipRadio = JBRadioButton()

    /** 开启后由 [com.github.xucux.i18ntranslate.translation.remote.PluginHttpApiClient] 将 HTTP 请求/响应记入 idea.log（`I18N::` 前缀）。 */
    private val httpDebugLogCheck = JBCheckBox()

    /** 只读：翻译引擎 API 总调用次数。 */
    private val statsTotalLabel = JBLabel()

    /** 只读：成功调用次数。 */
    private val statsOkLabel = JBLabel()

    /** 只读：失败调用次数。 */
    private val statsFailLabel = JBLabel()

    /** 只读：累计计费量（WordCount / UsedAmount / billed_characters 等汇总）。 */
    private val statsWordsValueLabel = JBLabel()

    /** 项目源消息文件路径（浏览选择，对应 [ProjectI18nState.sourceFilePath]）。 */
    private val sourcePathField = TextFieldWithBrowseButton().apply {
        textField.columns = 42
    }

    /** 源消息文件所表示的自然语言（[ProjectI18nState.sourceLanguage]）。 */
    private val sourceLangCombo = ComboBox(SupportedLanguage.values()).apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                text = (value as? SupportedLanguage)?.displayLabelWithCode ?: ""
            }
        }
        setMinimumAndPreferredWidth(JBUI.scale(160))
    }

    /** 动态添加多行「目标路径 + 目标语种」的垂直容器。 */
    private val targetsPanel = JPanel(GridLayout(0, 1))

    /** 与 [targetsPanel] 中各行一一对应的 [TargetRow] 列表，便于序列化与删除。 */
    private val targetRows = mutableListOf<TargetRow>()

    /** 本设置页界面文案预览语言（[GlobalPluginState.uiLanguage]，非 IDE 界面语言）。 */
    private val uiLangCombo = ComboBox(PluginUiLanguage.values())

    /** 打开当前所选引擎的凭证弹窗（[EngineCredentialsDialog]）。 */
    private lateinit var credButton: JButton

    /** 新增一条目标语言行（路径 + 语种）。 */
    private lateinit var addTarget: JButton

    /** 常规区顶部灰色说明（配置落盘路径提示）。 */
    private lateinit var usageHintLabel: JBLabel

    /** 分区标题：「常规」。 */
    private lateinit var generalSectionLabel: JBLabel

    /** 表单项标签：插件界面语言。 */
    private lateinit var uiLanguageLabel: JBLabel

    /** 表单项标签：翻译引擎。 */
    private lateinit var engineLabel: JBLabel

    /** 表单项标签：文件编码。 */
    private lateinit var fileEncodingLabel: JBLabel

    /** 表单项标签：目标写入模式。 */
    private lateinit var writeModeLabel: JBLabel

    /** 「自定义编码」复选框旁说明文字。 */
    private lateinit var customCharsetCaption: JBLabel

    /** 分区标题：「记录」/ 用量统计。 */
    private lateinit var recordsSectionLabel: JBLabel

    /** 表单项标签：引擎调用总次数。 */
    private lateinit var statsCallsLabel: JBLabel

    /** 表单项标签：成功次数。 */
    private lateinit var statsSuccessLabel: JBLabel

    /** 表单项标签：失败次数。 */
    private lateinit var statsFailedLabel: JBLabel

    /** 表单项标签：已翻译总量。 */
    private lateinit var statsWordsTotalLabel: JBLabel

    /** 将内存中的统计计数清零并写回磁盘。 */
    private lateinit var statsResetButton: JButton

    /** 分区标题：「项目国际化配置」。 */
    private lateinit var projI18nSectionLabel: JBLabel

    /** 表单项标签：源文件路径。 */
    private lateinit var sourceFileLabel: JBLabel

    /** 表单项标签：源语言。 */
    private lateinit var sourceLangLabel: JBLabel

    /** 表单项标签：目标语言配置区标题。 */
    private lateinit var targetsSectionLabel: JBLabel
    /** 为 true 时忽略 [uiLangCombo] 的 ActionListener，避免程序化改选中项时递归刷新文案。 */
    private var langComboListenerMuted = false

    /** 懒加载创建的内容根面板。 */
    private var rootPanel: JPanel? = null

    /** 首次调用时 [buildPanel] 并缓存 [rootPanel]。 */
    private fun ensureUi(): JPanel {
        if (rootPanel == null) {
            rootPanel = buildPanel()
        }
        return rootPanel!!
    }

    /**
     * 为 icon 按钮提供悬浮/按下背景反馈，提升可点击感知。
     */
    private fun applyIconButtonInteractionStyle(button: JButton) {
        val normalBg = JBColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))
        val hoverBg = JBColor(Color(0xE8EFF7), Color(0x3C4148))
        val pressedBg = JBColor(Color(0xD4E3F3), Color(0x4A5058))
        button.isOpaque = true
        button.isFocusPainted = false
        button.background = normalBg
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (button.isEnabled) button.background = hoverBg
            }

            override fun mouseExited(e: MouseEvent) {
                button.background = normalBg
            }

            override fun mousePressed(e: MouseEvent) {
                if (button.isEnabled) button.background = pressedBg
            }

            override fun mouseReleased(e: MouseEvent) {
                if (button.isEnabled && button.contains(e.point)) {
                    button.background = hoverBg
                    return
                }
                button.background = normalBg
            }
        })
    }

    /** 按 [projectSnapshot.targets] 重建目标行 UI。 */
    private fun rebuildTargetRows() {
        targetsPanel.removeAll()
        targetRows.clear()
        projectSnapshot.targets.forEach { t ->
            val row = TargetRow(project, t.filePath, t.language) { r ->
                targetRows.remove(r)
                targetsPanel.remove(r.panel)
                targetsPanel.revalidate()
                targetsPanel.repaint()
            }
            targetRows.add(row)
            targetsPanel.add(row.panel)
            row.refreshTexts()
        }
        targetsPanel.revalidate()
        targetsPanel.repaint()
    }

    /** 根据 [I18nTranslateBundle] 与当前预览语言刷新所有标签与按钮文案。 */
    private fun applyBundleTexts() {
        usageHintLabel.text = "<html>${I18nTranslateBundle.message("settings.usage.hint")}</html>"
        generalSectionLabel.text = I18nTranslateBundle.message("settings.general")
        uiLanguageLabel.text = I18nTranslateBundle.message("settings.ui.language")
        engineLabel.text = I18nTranslateBundle.message("settings.translate.engine")
        fileEncodingLabel.text = I18nTranslateBundle.message("settings.file.encoding")
        writeModeLabel.text = I18nTranslateBundle.message("settings.write.mode")
        customCharsetCaption.text = I18nTranslateBundle.message("settings.custom.encoding")
        recordsSectionLabel.text = I18nTranslateBundle.message("settings.records")
        statsCallsLabel.text = I18nTranslateBundle.message("settings.engine.calls")
        statsSuccessLabel.text = I18nTranslateBundle.message("settings.engine.success")
        statsFailedLabel.text = I18nTranslateBundle.message("settings.engine.failed")
        statsWordsTotalLabel.text = I18nTranslateBundle.message("settings.engine.words.total")
        projI18nSectionLabel.text = I18nTranslateBundle.message("settings.i18n.project")
        sourceFileLabel.text = I18nTranslateBundle.message("settings.source.file")
        sourceLangLabel.text = I18nTranslateBundle.message("settings.source.lang")
        targetsSectionLabel.text = I18nTranslateBundle.message("settings.targets")
        overwriteRadio.text = I18nTranslateBundle.message("settings.write.overwrite")
        skipRadio.text = I18nTranslateBundle.message("settings.write.skip")
        httpDebugLogCheck.text = I18nTranslateBundle.message("settings.http.debug.log")
        credButton.toolTipText = I18nTranslateBundle.message("settings.engine.credentials")
        statsResetButton.text = I18nTranslateBundle.message("settings.stats.reset")
        addTarget.text = I18nTranslateBundle.message("settings.add.target")
        targetRows.forEach { it.refreshTexts() }
    }

    /**
     * 设置页中「目标语言」的一行 UI，对应持久化结构 [TargetEntry]（路径 + 语种）。
     *
     * @param project 文件选择器上下文（单文件）
     * @param initialPath 初始目标消息文件磁盘路径
     * @param initialLang 该行所表示的目标自然语言
     * @param onRemove 点击删除时从 [targetRows] 与 [targetsPanel] 移除此行
     */
    private inner class TargetRow(
        project: Project,
        initialPath: String,
        initialLang: SupportedLanguage,
        private val onRemove: (TargetRow) -> Unit,
    ) {
        /** 目标消息文件路径（带浏览按钮，写入 [TargetEntry.filePath]）。 */
        val pathField = TextFieldWithBrowseButton().apply {
            textField.columns = 42
        }

        /**
         * 该目标文件对应的自然语言（与 [sourceLangCombo] 共用渲染器；
         * 写入 [TargetEntry.language]）。
         */
        val langCombo = ComboBox(SupportedLanguage.values()).apply {
            renderer = sourceLangCombo.renderer
            selectedItem = initialLang
            setMinimumAndPreferredWidth(JBUI.scale(160))
        }

        /** 表单项标签：目标文件路径（文案来自 Bundle `settings.target.path`）。 */
        private val pathCaption = JBLabel()

        /** 表单项标签：目标语言（文案来自 Bundle `settings.target.lang`）。 */
        private val langCaption = JBLabel()

        /**
         * 删除本行配置；图标为 GC，无文字，悬浮提示「删除此目标」。
         * 样式由 [applyIconButtonInteractionStyle] 与 [I18nTranslateBundle] 统一维护。
         */
        private val removeButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = ""
            isContentAreaFilled = false
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(24, 24)
            minimumSize = preferredSize
            applyIconButtonInteractionStyle(this)
            addActionListener { onRemove(this@TargetRow) }
        }

        /**
         * 单行水平布局容器（左起：路径标签、路径框、语种标签、语种下拉、删除键），
         * 作为子组件加入 [targetsPanel]。
         */
        val panel: JPanel = JPanel(BorderLayout()).apply {
            pathField.text = initialPath
            pathField.addBrowseFolderListener(
                I18nTranslateBundle.message("settings.target.file.chooser"),
                null,
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
            /** 单行内控件横向排列，左对齐、间距 8/4。 */
            val north = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
            north.add(pathCaption)
            north.add(pathField)
            north.add(langCaption)
            north.add(langCombo)
            north.add(removeButton)
            add(north, BorderLayout.CENTER)
        }

        /** 同步路径/语言标签与删除按钮提示的 ResourceBundle 文案。 */
        fun refreshTexts() {
            pathCaption.text = I18nTranslateBundle.message("settings.target.path")
            langCaption.text = I18nTranslateBundle.message("settings.target.lang")
            removeButton.toolTipText = I18nTranslateBundle.message("settings.remove.target")
        }

        /** 将当前 UI 状态收敛为持久化用的 [TargetEntry]。 */
        fun toEntry(): TargetEntry =
            TargetEntry(pathField.text.trim(), langCombo.selectedItem as SupportedLanguage)
    }

    /** 装配常规 / 统计 / 项目 i18n 三块表单并返回根面板。 */
    private fun buildPanel(): JPanel {
        val app = ApplicationManager.getApplication()
        globalSnapshot = app.getService(GlobalPluginConfigService::class.java).getState().copy()
        projectSnapshot = project.getService(ProjectI18nConfigService::class.java).getState().let {
            it.copy(targets = it.targets.map { e -> TargetEntry(e.filePath, e.language) }.toMutableList())
        }

        engineCombo.selectedItem = globalSnapshot.translateEngine
        charsetCombo.selectedItem = globalSnapshot.charsetName.ifBlank { "UTF-8" }.let { c ->
            charsetPresets.find { it.equals(c, true) } ?: c
        }
        customCharsetCheck.isSelected = globalSnapshot.customCharset
        customCharsetField.text = globalSnapshot.charsetName
        customCharsetField.isEnabled = globalSnapshot.customCharset

        when (globalSnapshot.writeMode) {
            FileWriteMode.OVERWRITE -> overwriteRadio.isSelected = true
            FileWriteMode.SKIP -> skipRadio.isSelected = true
        }
        httpDebugLogCheck.isSelected = globalSnapshot.httpDebugLogging

        val modeGroup = ButtonGroup()
        modeGroup.add(overwriteRadio)
        modeGroup.add(skipRadio)

        usageHintLabel = JBLabel().apply { foreground = JBColor.GRAY }
        generalSectionLabel = JBLabel()
        uiLanguageLabel = JBLabel()
        engineLabel = JBLabel()
        fileEncodingLabel = JBLabel()
        writeModeLabel = JBLabel()
        customCharsetCaption = JBLabel()
        recordsSectionLabel = JBLabel()
        statsCallsLabel = JBLabel()
        statsSuccessLabel = JBLabel()
        statsFailedLabel = JBLabel()
        statsWordsTotalLabel = JBLabel()
        projI18nSectionLabel = JBLabel()
        sourceFileLabel = JBLabel()
        sourceLangLabel = JBLabel()
        targetsSectionLabel = JBLabel()

        uiLangCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                text = when (value as? PluginUiLanguage) {
                    PluginUiLanguage.ENGLISH -> I18nTranslateBundle.message("settings.ui.lang.english")
                    PluginUiLanguage.CHINESE -> I18nTranslateBundle.message("settings.ui.lang.chinese")
                    else -> ""
                }
            }
        }
        uiLangCombo.addActionListener {
            if (langComboListenerMuted) return@addActionListener
            I18nTranslateBundle.settingsPreviewLanguage = uiLangCombo.selectedItem as PluginUiLanguage
            applyBundleTexts()
        }
        langComboListenerMuted = true
        uiLangCombo.selectedItem = globalSnapshot.uiLanguage
        langComboListenerMuted = false

        charsetCombo.addActionListener {
            if (!customCharsetCheck.isSelected) {
                customCharsetField.text = charsetCombo.selectedItem as? String ?: "UTF-8"
            }
        }
        customCharsetCheck.addActionListener {
            customCharsetField.isEnabled = customCharsetCheck.isSelected
            if (!customCharsetCheck.isSelected) {
                customCharsetField.text = charsetCombo.selectedItem as? String ?: "UTF-8"
            }
        }

        credButton = JButton(AllIcons.General.Settings).apply {
            isContentAreaFilled = false
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(24, 24)
            minimumSize = preferredSize
            horizontalAlignment = JButton.LEFT
            horizontalTextPosition = JButton.RIGHT
            toolTipText = I18nTranslateBundle.message("settings.engine.credentials")
            applyIconButtonInteractionStyle(this)
        }
        credButton.addActionListener {
            val eng = engineCombo.selectedItem as TranslateEngine
            val dlg = when (eng) {
                TranslateEngine.ALIYUN -> EngineCredentialsDialog(
                    eng,
                    globalSnapshot.aliyunAccessKeyId,
                    globalSnapshot.aliyunAccessKeySecret,
                )
                TranslateEngine.TENCENT -> EngineCredentialsDialog(
                    eng,
                    globalSnapshot.tencentSecretId,
                    globalSnapshot.tencentSecretKey,
                    globalSnapshot.tencentRegion,
                )
                TranslateEngine.DEEPL -> EngineCredentialsDialog(
                    eng,
                    "",
                    globalSnapshot.deepLAuthKey,
                )
            }
            if (dlg.showAndGet()) {
                when (eng) {
                    TranslateEngine.ALIYUN -> {
                        globalSnapshot = globalSnapshot.copy(
                            aliyunAccessKeyId = dlg.accessId,
                            aliyunAccessKeySecret = dlg.accessSecret,
                        )
                    }
                    TranslateEngine.TENCENT -> {
                        globalSnapshot = globalSnapshot.copy(
                            tencentSecretId = dlg.accessId,
                            tencentSecretKey = dlg.accessSecret,
                            tencentRegion = dlg.tencentRegionCode,
                        )
                    }
                    TranslateEngine.DEEPL -> {
                        globalSnapshot = globalSnapshot.copy(
                            deepLAuthKey = dlg.accessSecret,
                        )
                    }
                }
            }
        }

        sourcePathField.text = projectSnapshot.sourceFilePath
        sourcePathField.addBrowseFolderListener(
            I18nTranslateBundle.message("settings.source.file.chooser"),
            null,
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
        sourceLangCombo.selectedItem = projectSnapshot.sourceLanguage

        addTarget = JButton()
        addTarget.addActionListener {
            val row = TargetRow(project, "", SupportedLanguage.ENGLISH) { r ->
                targetRows.remove(r)
                targetsPanel.remove(r.panel)
                targetsPanel.revalidate()
                targetsPanel.repaint()
            }
            targetRows.add(row)
            targetsPanel.add(row.panel)
            row.refreshTexts()
            targetsPanel.revalidate()
            targetsPanel.repaint()
        }

        statsResetButton = JButton()
        statsResetButton.addActionListener {
            ApplicationManager.getApplication().getService(I18nStatsService::class.java).resetAllCountsAndPersist()
            refreshStats()
        }

        rebuildTargetRows()

        refreshStats()

        charsetCombo.preferredSize = JBUI.size(JBUI.scale(140), JBUI.scale(28))
        charsetCombo.minimumSize = charsetCombo.preferredSize
        val charsetRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        charsetRow.add(charsetCombo)
        charsetRow.add(customCharsetCaption)
        charsetRow.add(customCharsetCheck)
        charsetRow.add(customCharsetField)

        val modePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        modePanel.add(overwriteRadio)
        modePanel.add(skipRadio)

        val engineRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        engineRow.add(engineCombo)
        engineRow.add(credButton)

        val general = FormBuilder.createFormBuilder()
            .addComponent(usageHintLabel)
            .addSeparator()
            .addComponent(Box.createVerticalStrut(JBUI.scale(8)) as javax.swing.JComponent)
            .addLabeledComponent(generalSectionLabel, JPanel(BorderLayout()))
            .addLabeledComponent(uiLanguageLabel, uiLangCombo)
            .addLabeledComponent(engineLabel, engineRow)
            .addLabeledComponent(fileEncodingLabel, charsetRow)
            .addLabeledComponent(writeModeLabel, modePanel)
            .addComponent(httpDebugLogCheck)
            .panel

        val stats = FormBuilder.createFormBuilder()
            .addSeparator()
            .addComponent(Box.createVerticalStrut(JBUI.scale(8)) as javax.swing.JComponent)
            .addLabeledComponent(recordsSectionLabel, JPanel())
            .addLabeledComponent(statsCallsLabel, statsTotalLabel)
            .addLabeledComponent(statsSuccessLabel, statsOkLabel)
            .addLabeledComponent(statsFailedLabel, statsFailLabel)
            .addLabeledComponent(statsWordsTotalLabel, statsWordsValueLabel)
            .addComponent(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(statsResetButton) })
            .panel

        val targetsHeader = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        targetsHeader.add(targetsSectionLabel)
        targetsHeader.add(addTarget)

        val proj = FormBuilder.createFormBuilder()
            .addSeparator()
            .addComponent(Box.createVerticalStrut(JBUI.scale(8)) as javax.swing.JComponent)
            .addLabeledComponent(projI18nSectionLabel, JPanel())
            .addLabeledComponent(sourceFileLabel, sourcePathField)
            .addLabeledComponent(sourceLangLabel, sourceLangCombo)
            .addComponent(targetsHeader)
            .addComponent(targetsPanel)
            .panel

        val box = Box.createVerticalBox()
        box.add(general)
        box.add(stats)
        box.add(proj)

        applyBundleTexts()

        return JPanel(BorderLayout()).apply {
            add(box, BorderLayout.NORTH)
        }
    }

    /** 从 [I18nStatsService] 拉取当前内存计数刷新只读统计标签。 */
    private fun refreshStats() {
        val st = ApplicationManager.getApplication().getService(I18nStatsService::class.java)
        statsTotalLabel.text = st.totalCalls.get().toString()
        statsOkLabel.text = st.successCalls.get().toString()
        statsFailLabel.text = st.failedCalls.get().toString()
        statsWordsValueLabel.text = st.translatedWordsTotal.get().toString()
    }

    /** 设置树中显示的名称（随 Bundle 语言变化）。 */
    override fun getDisplayName(): String =
        I18nTranslateBundle.message("settings.display.name")

    /** IDE 设置框架入口：返回本页 Swing 根。 */
    override fun createComponent(): javax.swing.JComponent = ensureUi()

    /** 比较表单与已持久化的全局/项目服务状态是否一致。 */
    override fun isModified(): Boolean {
        if (rootPanel == null) return false
        val g = readGlobalFromUi()
        val p = readProjectFromUi()
        return g != ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java).getState() ||
            p != project.getService(ProjectI18nConfigService::class.java).getState()
    }

    /** 从控件读取全局项；云凭证仍取自 [globalSnapshot]（由凭证弹窗单独更新）。 */
    private fun readGlobalFromUi(): GlobalPluginState {
        val charset = if (customCharsetCheck.isSelected) customCharsetField.text.trim().ifBlank { "UTF-8" }
        else (charsetCombo.selectedItem as? String) ?: "UTF-8"
        return GlobalPluginState(
            uiLanguage = uiLangCombo.selectedItem as PluginUiLanguage,
            translateEngine = engineCombo.selectedItem as TranslateEngine,
            charsetName = charset,
            customCharset = customCharsetCheck.isSelected,
            writeMode = if (skipRadio.isSelected) FileWriteMode.SKIP else FileWriteMode.OVERWRITE,
            aliyunAccessKeyId = globalSnapshot.aliyunAccessKeyId,
            aliyunAccessKeySecret = globalSnapshot.aliyunAccessKeySecret,
            tencentSecretId = globalSnapshot.tencentSecretId,
            tencentSecretKey = globalSnapshot.tencentSecretKey,
            tencentRegion = globalSnapshot.tencentRegion,
            deepLAuthKey = globalSnapshot.deepLAuthKey,
            httpDebugLogging = httpDebugLogCheck.isSelected,
            firstRunSettingsHintShown = globalSnapshot.firstRunSettingsHintShown,
        )
    }

    /** 从控件组装项目 i18n 状态（忽略空路径目标行）。 */
    private fun readProjectFromUi(): ProjectI18nState {
        val targets = targetRows.map { it.toEntry() }.filter { it.filePath.isNotBlank() }.toMutableList()
        return ProjectI18nState(
            sourceFilePath = sourcePathField.text.trim(),
            sourceLanguage = sourceLangCombo.selectedItem as SupportedLanguage,
            targets = targets,
        )
    }

    /** 将当前表单写入 Application/Project 服务并落盘；清空预览语言并顺带 [I18nStatsService.flushToDisk]。 */
    @Throws(ConfigurationException::class)
    override fun apply() {
        val g = readGlobalFromUi()
        val p = readProjectFromUi()
        ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java).applyState(g)
        project.getService(ProjectI18nConfigService::class.java).applyState(p)
        globalSnapshot = g
        projectSnapshot = p
        I18nTranslateBundle.settingsPreviewLanguage = null
        ApplicationManager.getApplication().getService(I18nStatsService::class.java).flushToDisk()
    }

    /** 自磁盘重载并回填所有控件（含目标行重建与统计刷新）。 */
    override fun reset() {
        ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java).reloadFromDisk()
        project.getService(ProjectI18nConfigService::class.java).reloadFromDisk()
        // rebuild UI from disk — simplest: replace root ... Configurable typically recreates; IDE calls createComponent once.
        // For simplicity require reopening settings or manual refresh — many plugins recreate panel on reset:
        // Here we mutate fields from reload:
        val g = ApplicationManager.getApplication().getService(GlobalPluginConfigService::class.java).getState()
        val pr = project.getService(ProjectI18nConfigService::class.java).getState()
        globalSnapshot = g.copy()
        projectSnapshot = pr.copy(targets = pr.targets.map { TargetEntry(it.filePath, it.language) }.toMutableList())
        engineCombo.selectedItem = g.translateEngine
        langComboListenerMuted = true
        uiLangCombo.selectedItem = g.uiLanguage
        langComboListenerMuted = false
        I18nTranslateBundle.settingsPreviewLanguage = null
        customCharsetCheck.isSelected = g.customCharset
        charsetCombo.selectedItem = charsetPresets.find { it.equals(g.charsetName, true) } ?: g.charsetName
        customCharsetField.text = g.charsetName
        when (g.writeMode) {
            FileWriteMode.OVERWRITE -> overwriteRadio.isSelected = true
            FileWriteMode.SKIP -> skipRadio.isSelected = true
        }
        httpDebugLogCheck.isSelected = g.httpDebugLogging
        sourcePathField.text = pr.sourceFilePath
        sourceLangCombo.selectedItem = pr.sourceLanguage
        projectSnapshot.targets.clear()
        projectSnapshot.targets.addAll(pr.targets)
        rebuildTargetRows()
        refreshStats()
        if (rootPanel != null) {
            applyBundleTexts()
        }
    }

    /** 关闭设置页时结束预览语言覆盖。 */
    override fun dispose() {
        I18nTranslateBundle.settingsPreviewLanguage = null
    }
}
