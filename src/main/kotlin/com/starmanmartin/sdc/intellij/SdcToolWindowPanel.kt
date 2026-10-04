package com.starmanmartin.sdc.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.starmanmartin.sdc.intellij.tree.SdcControllerNode
import com.starmanmartin.sdc.intellij.tree.SdcGroupNode
import com.starmanmartin.sdc.intellij.tree.SdcIcons
import com.starmanmartin.sdc.intellij.tree.SdcInfoNode
import com.starmanmartin.sdc.intellij.tree.SdcModelNode
import com.starmanmartin.sdc.intellij.tree.SdcTreeNode
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultTreeCellRenderer

class SdcToolWindowPanel(private val project: Project) : JBPanel<SdcToolWindowPanel>(BorderLayout()) {

    private val tree = JTree(DefaultTreeModel(DefaultMutableTreeNode("SimpleDomControl")))
    private val statusLabel = JLabel(" ")
    private var refreshing = false

    init {
        val group = DefaultActionGroup().apply { add(RefreshAction()) }
        val toolbar = ActionManager.getInstance().createActionToolbar("SdcToolWindowToolbar", group, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = SdcTreeRenderer()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                (node.userObject as? SdcTreeNode)?.onDoubleClick(project)
            }
        })
        add(JBScrollPane(tree), BorderLayout.CENTER)

        statusLabel.border = EmptyBorder(2, 8, 2, 8)
        add(statusLabel, BorderLayout.SOUTH)

        refresh()
    }

    private inner class RefreshAction :
        AnAction("Refresh SDC Infos", "Run sdc_get_controller_infos and sdc_get_model_infos", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refresh()
        }
    }

    fun refresh() {
        if (refreshing || project.isDisposed) return
        refreshing = true
        setStatus("Updating SimpleDomControl infos...", UIUtil.getLabelForeground())
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
                    result != null -> applySnapshot(result)
                    error != null -> showError(error!!)
                }
            }
        }.queue()
    }

    private fun applySnapshot(snapshot: SdcSnapshot) {
        tree.model = DefaultTreeModel(buildTree(snapshot))
        expandTopLevel()
        val controllerCount = snapshot.controllers.values.sumOf { it.size }
        val parts = mutableListOf("Updated", "$controllerCount controllers,", "${snapshot.models.size} models")
        if (snapshot.errors.isNotEmpty()) {
            parts.add("with warnings (${snapshot.errors.size})")
            setStatus(parts.joinToString(" "), UIUtil.getErrorForeground())
        } else {
            setStatus(parts.joinToString(" "), UIUtil.getLabelForeground())
        }
    }

    private fun showError(error: SdcCommandException) {
        val message = buildString {
            append(error.message ?: "Unknown error")
            append("\n\n")
            append(error.output.takeLast(2000).trim())
        }.ifBlank { "no command output" }
        setStatus(error.message ?: "Unknown error", UIUtil.getErrorForeground())
        val errorRoot = DefaultMutableTreeNode(SdcGroupNode("Error - SDC infos unavailable", SdcIcons.app, emptyList()), true)
        val details = DefaultMutableTreeNode(SdcInfoNode(message.replace("\n", " ").take(200), SdcIcons.info), false)
        errorRoot.add(details)
        tree.model = DefaultTreeModel(errorRoot)
        tree.expandRow(0)
        Messages.showErrorDialog(project, message, "SimpleDomControl")
    }

    private fun setStatus(text: String, color: java.awt.Color) {
        statusLabel.text = text
        statusLabel.foreground = color
    }

    private fun buildTree(snapshot: SdcSnapshot): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(SdcGroupNode("SimpleDomControl", SdcIcons.root, emptyList()), true)

        val controllerGroups = snapshot.controllers
            .filter { it.value.isNotEmpty() }
            .map { (app, controllers) ->
                SdcGroupNode("$app (${controllers.size})", SdcIcons.app, controllers.map { SdcControllerNode(it) })
            }
        root.add(toTreeNode(SdcGroupNode("Controllers", SdcIcons.root, controllerGroups)))

        val modelGroups = snapshot.models
            .groupBy { it.app ?: "?" }
            .map { (app, models) ->
                SdcGroupNode("$app (${models.size})", SdcIcons.app, models.map { SdcModelNode(it) })
            }
        root.add(toTreeNode(SdcGroupNode("Models", SdcIcons.root, modelGroups)))

        snapshot.errors.forEach { error ->
            root.add(DefaultMutableTreeNode(SdcInfoNode("warning: ${error.take(300)}", SdcIcons.info), false))
        }
        return root
    }

    private fun toTreeNode(node: SdcTreeNode): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node, node.children.isNotEmpty())
        node.children.forEach { treeNode.add(toTreeNode(it)) }
        return treeNode
    }

    private fun expandTopLevel() {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private class SdcTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): java.awt.Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = (value as? DefaultMutableTreeNode)?.userObject as? SdcTreeNode
            if (node != null) {
                text = node.label
                openIcon = node.icon
                closedIcon = node.icon
                leafIcon = node.icon
            }
            return this
        }
    }
}
