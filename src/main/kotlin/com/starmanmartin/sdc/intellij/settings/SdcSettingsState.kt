package com.starmanmartin.sdc.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import java.nio.file.Path

@State(name = "SimpleDomControlSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SdcSettingsState : PersistentStateComponent<SdcSettingsState> {

    /** Interpreter used to run the Django management commands; must have Django installed. */
    var pythonInterpreter: String = "python3"

    /**
     * Directory containing manage.py of the SDC project. Relative paths are resolved
     * against the IntelliJ project root; empty means the project root itself.
     */
    var managePyDir: String = ""

    /** Custom "Unknown HTML tag" entries added by the plugin (controller tags), see SdcHtmlInspectionSync. */
    var managedHtmlTags: MutableList<String> = mutableListOf()

    /** Custom "Unknown HTML attribute" entries added by the plugin (sdc_<event>), see SdcHtmlInspectionSync. */
    var managedHtmlAttributes: MutableList<String> = mutableListOf()

    override fun getState(): SdcSettingsState = this

    override fun loadState(state: SdcSettingsState) {
        pythonInterpreter = state.pythonInterpreter
        managePyDir = state.managePyDir
        managedHtmlTags = state.managedHtmlTags.toMutableList()
        managedHtmlAttributes = state.managedHtmlAttributes.toMutableList()
    }

    fun resolveProjectDir(projectRoot: Path): Path {
        val value = managePyDir.trim()
        if (value.isEmpty()) return projectRoot
        val configured = Path.of(value)
        return if (configured.isAbsolute) configured.normalize() else projectRoot.resolve(configured).normalize()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SdcSettingsState = project.getService(SdcSettingsState::class.java)
    }
}
