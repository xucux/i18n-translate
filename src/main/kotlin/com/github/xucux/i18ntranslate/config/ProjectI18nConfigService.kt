package com.github.xucux.i18ntranslate.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 当前打开项目的 i18n 路径配置（源 / 多目标）。[getState] 返回副本。
 */
@Service(Service.Level.PROJECT)
class ProjectI18nConfigService(private val project: Project) {
    @Volatile
    private var state: ProjectI18nState = ProjectI18nState.load(project)

    /** 返回当前项目 i18n 配置的深拷贝（含 [TargetEntry] 列表副本），避免外部修改内部状态。 */
    fun getState(): ProjectI18nState =
        state.copy(targets = state.targets.map { TargetEntry(it.filePath, it.language) }.toMutableList())

    /** 更新内存并写入项目 `.idea` 下 properties。 */
    fun applyState(newState: ProjectI18nState) {
        state = newState.copy(
            targets = newState.targets.map { TargetEntry(it.filePath, it.language) }.toMutableList(),
        )
        ProjectI18nState.save(project, state)
    }

    /** 从磁盘重新加载本项目配置。 */
    fun reloadFromDisk() {
        state = ProjectI18nState.load(project)
    }
}
