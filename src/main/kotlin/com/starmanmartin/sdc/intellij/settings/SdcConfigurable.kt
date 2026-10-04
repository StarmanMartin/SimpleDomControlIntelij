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

    private var pythonField: JTextField? = null
    private var dirField: JTextField? = null

    override fun getDisplayName(): String = "SimpleDomControl"

    override fun createComponent(): JComponent {
        val settings = SdcSettingsState.getInstance(project)
        val python = JTextField(settings.pythonInterpreter, 30)
        val dir = JTextField(settings.managePyDir, 30)
        pythonField = python
        dirField = dir

        val pythonBrowse = JButton("...").apply {
            addActionListener {
                FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), project, null)
                    ?.let { python.text = it.path }
            }
        }
        val dirBrowse = JButton("...").apply {
            addActionListener {
                FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null)
                    ?.let { dir.text = it.path }
            }
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Python interpreter:", fieldWithButton(python, pythonBrowse))
            .addLabeledComponent("manage.py directory:", fieldWithButton(dir, dirBrowse))
            .addComponent(JLabel("<html>Python interpreter must have Django installed.<br/>" +
                    "manage.py directory: root of the SDC-initialized Django project;<br/>" +
                    "leave empty to use the project root of the open IntelliJ project.</html>"))
            .panel
    }

    private fun fieldWithButton(field: JTextField, button: JButton): JPanel =
        JPanel(BorderLayout()).apply {
            add(field, BorderLayout.CENTER)
            add(button, BorderLayout.EAST)
        }

    override fun isModified(): Boolean {
        val settings = SdcSettingsState.getInstance(project)
        return pythonField?.text?.trim() != settings.pythonInterpreter ||
                dirField?.text?.trim() != settings.managePyDir
    }

    override fun apply() {
        val settings = SdcSettingsState.getInstance(project)
        settings.pythonInterpreter = pythonField?.text?.trim().orEmpty().ifBlank { "python3" }
        settings.managePyDir = dirField?.text?.trim().orEmpty()
    }

    override fun reset() {
        val settings = SdcSettingsState.getInstance(project)
        pythonField?.text = settings.pythonInterpreter
        dirField?.text = settings.managePyDir
    }

    override fun disposeUIResources() {
        pythonField = null
        dirField = null
    }
}
