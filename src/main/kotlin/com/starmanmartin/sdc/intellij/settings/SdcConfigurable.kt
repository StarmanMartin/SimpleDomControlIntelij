package com.starmanmartin.sdc.intellij.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import java.awt.BorderLayout

class SdcConfigurable(private val project: Project) : Configurable {

    private var dirField: JTextField? = null

    override fun getDisplayName(): String = "SimpleDomControl"

    override fun createComponent(): JComponent {
        val settings = SdcSettingsState.getInstance(project)
        val dir = JTextField(settings.managePyDir, 30)
        dirField = dir

        val dirBrowse = JButton("...").apply {
            addActionListener {
                FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null)
                    ?.let { dir.text = it.path }
            }
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("manage.py directory:", fieldWithButton(dir, dirBrowse))
            .addComponent(JLabel("<html>manage.py directory: root of the SDC-initialized Django project;<br/>" +
                    "leave empty to use the project root of the open IntelliJ project.<br/>" +
                    "The Python interpreter is read from Assets/.sdc_python_env, which SDC writes<br/>" +
                    "whenever the server is started (python manage.py runserver).</html>"))
            .panel
    }

    private fun fieldWithButton(field: JTextField, button: JButton): JPanel =
        JPanel(BorderLayout()).apply {
            add(field, BorderLayout.CENTER)
            add(button, BorderLayout.EAST)
        }

    override fun isModified(): Boolean {
        val settings = SdcSettingsState.getInstance(project)
        return dirField?.text?.trim() != settings.managePyDir
    }

    override fun apply() {
        val settings = SdcSettingsState.getInstance(project)
        settings.managePyDir = dirField?.text?.trim().orEmpty()
    }

    override fun reset() {
        val settings = SdcSettingsState.getInstance(project)
        dirField?.text = settings.managePyDir
    }

    override fun disposeUIResources() {
        dirField = null
    }
}
