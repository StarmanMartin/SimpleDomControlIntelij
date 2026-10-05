package com.starmanmartin.sdc.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Registers the "SDC" tool window on the left side of the editor, with a "Controllers" and a "Models" tab. */
class SdcToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val controller = SdcToolWindowController(project)
        val contentFactory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(
            contentFactory.createContent(controller.controllersPanel, SdcTabKind.CONTROLLERS.title, false)
        )
        toolWindow.contentManager.addContent(
            contentFactory.createContent(controller.modelsPanel, SdcTabKind.MODELS.title, false)
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
