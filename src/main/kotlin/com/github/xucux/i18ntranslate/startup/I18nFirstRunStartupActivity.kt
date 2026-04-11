package com.github.xucux.i18ntranslate.startup

import com.github.xucux.i18ntranslate.bundle.I18nTranslateBundle
import com.github.xucux.i18ntranslate.config.GlobalPluginConfigService
import com.github.xucux.i18ntranslate.view.settings.I18nTranslateConfigurable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * 首次使用插件时（全局配置中尚未标记已提示）弹出通知，引导打开 **I18N Translate** 设置页；仅触发一次。
 */
class I18nFirstRunStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        val app = ApplicationManager.getApplication()
        val svc = app.getService(GlobalPluginConfigService::class.java)

        val shouldNotify = synchronized(LOCK) {
            val state = svc.getState()
            if (state.firstRunSettingsHintShown) return@synchronized false
            svc.applyState(state.copy(firstRunSettingsHintShown = true))
            true
        }
        if (!shouldNotify) return

        app.invokeLater {
            if (project.isDisposed) return@invokeLater
            val notification = Notification(
                NOTIFICATION_GROUP_ID,
                I18nTranslateBundle.message("notification.firstRun.title"),
                I18nTranslateBundle.message("notification.firstRun.content"),
                NotificationType.INFORMATION,
            )
            notification.addAction(
                NotificationAction.createSimpleExpiring(
                    I18nTranslateBundle.message("notification.firstRun.openSettings"),
                ) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        I18nTranslateConfigurable::class.java,
                    )
                },
            )
            notification.notify(project)
        }
    }

    companion object {
        private val LOCK = Any()
        /** 与 [plugin.xml] 中 `notificationGroup` 的 `id` 一致。 */
        const val NOTIFICATION_GROUP_ID: String = "I18nTranslate.firstRun"
    }
}
