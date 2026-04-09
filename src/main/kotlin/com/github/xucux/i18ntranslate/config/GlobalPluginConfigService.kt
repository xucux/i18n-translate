package com.github.xucux.i18ntranslate.config

import com.intellij.openapi.components.Service

/**
 * Application 级全局配置的内存缓存与持久化入口。
 * [getState] 返回副本，避免外部误改内部引用。
 */
@Service(Service.Level.APP)
class GlobalPluginConfigService {
    @Volatile
    private var state: GlobalPluginState = GlobalPluginState.load()

    /** 当前内存中的全局配置快照（与已持久化内容一致，除非尚未 [applyState]）。 */
    fun getState(): GlobalPluginState = state.copy()

    /** 更新内存并写入 `general.properties`。 */
    fun applyState(newState: GlobalPluginState) {
        state = newState.copy()
        GlobalPluginState.save(state)
    }

    /** 丢弃内存修改，从磁盘重新 [GlobalPluginState.load]。 */
    fun reloadFromDisk() {
        state = GlobalPluginState.load()
    }
}
