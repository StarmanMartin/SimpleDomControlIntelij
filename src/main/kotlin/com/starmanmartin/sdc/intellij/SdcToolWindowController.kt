package com.starmanmartin.sdc.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Owns the "Controllers" and "Models" tabs of the SDC tool window. A refresh from either tab
 * fetches one [SdcSnapshot] (both management commands) and pushes it into both tabs.
 */
class SdcToolWindowController(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(SdcToolWindowController::class.java)
    }

    val controllersPanel = SdcToolWindowPanel(project, SdcTabKind.CONTROLLERS, ::refresh)
    val modelsPanel = SdcToolWindowPanel(project, SdcTabKind.MODELS, ::refresh)
    private val panels = listOf(controllersPanel, modelsPanel)
    private var refreshing = false

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing || project.isDisposed) return
        refreshing = true
        panels.forEach { it.showLoading() }
        object : Task.Backgroundable(project, "Loading SimpleDomControl infos", true) {
            var snapshot: SdcSnapshot? = null
            var error: SdcCommandException? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    snapshot = SdcInfoService.getInstance(project).fetchSnapshot()
                } catch (e: SdcCommandException) {
                    error = e
                } catch (e: Exception) {
                    error = SdcCommandException(e.message ?: e.javaClass.simpleName, "")
                }
            }

            override fun onSuccess() {
                // handled in onFinished()
            }

            override fun onFinished() {
                refreshing = false
                if (project.isDisposed) return
                val result = snapshot
                when {
                    result != null -> {
                        panels.forEach { it.showSnapshot(result) }
                        syncHtmlInspections(result)
                    }
                    error != null -> showError(error!!)
                }
            }
        }.queue()
    }

    private fun syncHtmlInspections(snapshot: SdcSnapshot) {
        try {
            SdcHtmlInspectionSync.sync(project, snapshot)
        } catch (e: Exception) {
            LOG.warn("Registering SDC tags/attributes in the HTML inspections failed", e)
        }
    }

    private fun showError(error: SdcCommandException) {
        val message = buildString {
            append(error.message ?: "Unknown error")
            append("\n\n")
            append(error.output.takeLast(2000).trim())
        }.ifBlank { "no command output" }
        panels.forEach { it.showError(message, error.message ?: "Unknown error") }
        Messages.showErrorDialog(project, message, "SimpleDomControl")
    }
}
