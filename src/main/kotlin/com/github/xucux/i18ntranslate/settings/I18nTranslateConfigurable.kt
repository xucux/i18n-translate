package com.github.xucux.i18ntranslate.settings

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
import com.github.xucux.i18ntranslate.lang.SupportedLanguage
import com.github.xucux.i18ntranslate.ui.EngineCredentialsDialog
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

    private val engineCombo = ComboBox(arrayOf(TranslateEngine.ALIYUN, TranslateEngine.TENCENT)).apply {
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
    private val charsetPresets = arrayOf("UTF-8", "GBK", "ISO-8859-1", "UTF-16")
    private val charsetCombo = ComboBox(charsetPresets)
    private val customCharsetCheck = JBCheckBox()
    private val customCharsetField = com.intellij.ui.components.JBTextField()

    private val overwriteRadio = JBRadioButton()
    private val skipRadio = JBRadioButton()

    private val statsTotalLabel = JBLabel()
    private val statsOkLabel = JBLabel()
    private val statsFailLabel = JBLabel()
    private val statsWordsValueLabel = JBLabel()

    private val sourcePathField = TextFieldWithBrowseButton().apply {
        textField.columns = 42
    }
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
    private val targetsPanel = JPanel(GridLayout(0, 1))
    private val targetRows = mutableListOf<TargetRow>()

    private val uiLangCombo = ComboBox(PluginUiLanguage.values())
    private lateinit var credButton: JButton
    private lateinit var addTarget: JButton
    private lateinit var usageHintLabel: JBLabel
    private lateinit var generalSectionLabel: JBLabel
    private lateinit var uiLanguageLabel: JBLabel
    private lateinit var engineLabel: JBLabel
    private lateinit var fileEncodingLabel: JBLabel
    private lateinit var writeModeLabel: JBLabel
    private lateinit var customCharsetCaption: JBLabel
    private lateinit var recordsSectionLabel: JBLabel
    private lateinit var statsCallsLabel: JBLabel
    private lateinit var statsSuccessLabel: JBLabel
    private lateinit var statsFailedLabel: JBLabel
    private lateinit var statsWordsTotalLabel: JBLabel
    private lateinit var statsResetButton: JButton
    private lateinit var projI18nSectionLabel: JBLabel
    private lateinit var sourceFileLabel: JBLabel
    private lateinit var sourceLangLabel: JBLabel
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
        credButton.toolTipText = I18nTranslateBundle.message("settings.engine.credentials")
        statsResetButton.text = I18nTranslateBundle.message("settings.stats.reset")
        addTarget.text = I18nTranslateBundle.message("settings.add.target")
        targetRows.forEach { it.refreshTexts() }
    }

    /**
     * 设置页中一行：目标路径浏览 + 语种下拉 + 删除按钮。
     * @param onRemove 点击删除时从列表与面板移除本行
     */
    private inner class TargetRow(
        project: Project,
        initialPath: String,
        initialLang: SupportedLanguage,
        private val onRemove: (TargetRow) -> Unit,
    ) {
        /** 目标消息文件路径（带浏览）。 */
        val pathField = TextFieldWithBrowseButton().apply {
            textField.columns = 42
        }
        /** 该行目标消息文件对应的自然语言。 */
        val langCombo = ComboBox(SupportedLanguage.values()).apply {
            renderer = sourceLangCombo.renderer
            selectedItem = initialLang
            setMinimumAndPreferredWidth(JBUI.scale(160))
        }
        private val pathCaption = JBLabel()
        private val langCaption = JBLabel()
        private val removeButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = ""
            isContentAreaFilled = false
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(24, 24)
            minimumSize = preferredSize
            applyIconButtonInteractionStyle(this)
            addActionListener { onRemove(this@TargetRow) }
        }
        /** 单行布局容器，供加入 [targetsPanel]。 */
        val panel: JPanel = JPanel(BorderLayout()).apply {
            pathField.text = initialPath
            pathField.addBrowseFolderListener(
                I18nTranslateBundle.message("settings.target.file.chooser"),
                null,
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
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
