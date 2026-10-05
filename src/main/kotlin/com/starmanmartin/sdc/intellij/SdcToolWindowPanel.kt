package com.starmanmartin.sdc.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
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

/** Which part of the snapshot a tool window tab shows. [command] prefixes that command's errors in [SdcSnapshot.errors]. */
enum class SdcTabKind(val title: String, val command: String) {
    CONTROLLERS("Controllers", "sdc_get_controller_infos"),
    MODELS("Models", "sdc_get_model_infos"),
}

/** One tab of the SDC tool window: a tree of either controllers or models. Data is pushed in by [SdcToolWindowController]. */
class SdcToolWindowPanel(
    private val project: Project,
    private val kind: SdcTabKind,
    private val onRefresh: () -> Unit,
) : JBPanel<SdcToolWindowPanel>(BorderLayout()) {

    private val tree = JTree(DefaultTreeModel(DefaultMutableTreeNode(kind.title)))
    private val statusLabel = JLabel(" ")

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
    }

    private inner class RefreshAction :
        AnAction("Refresh SDC Infos", "Run sdc_get_controller_infos and sdc_get_model_infos", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            onRefresh()
        }
    }

    fun showLoading() {
        setStatus("Updating SimpleDomControl infos...", UIUtil.getLabelForeground())
    }

    fun showSnapshot(snapshot: SdcSnapshot) {
        val errors = snapshot.errors.filter { it.startsWith("${kind.command}:") }
        tree.model = DefaultTreeModel(buildTree(snapshot, errors))
        expandTopLevel()
        val count = when (kind) {
            SdcTabKind.CONTROLLERS -> "${snapshot.controllers.values.sumOf { it.size }} controllers"
            SdcTabKind.MODELS -> "${snapshot.models.size} models"
        }
        if (errors.isNotEmpty()) {
            setStatus("Updated $count with warnings (${errors.size})", UIUtil.getErrorForeground())
        } else {
            setStatus("Updated $count", UIUtil.getLabelForeground())
        }
    }

    fun showError(message: String, statusText: String) {
        setStatus(statusText, UIUtil.getErrorForeground())
        val errorRoot = DefaultMutableTreeNode(SdcGroupNode("Error - SDC infos unavailable", SdcIcons.error, emptyList()), true)
        val details = DefaultMutableTreeNode(SdcInfoNode(message.replace("\n", " ").take(200), SdcIcons.error), false)
        errorRoot.add(details)
        tree.model = DefaultTreeModel(errorRoot)
        tree.expandRow(0)
    }

    private fun setStatus(text: String, color: java.awt.Color) {
        statusLabel.text = text
        statusLabel.foreground = color
    }

    private fun buildTree(snapshot: SdcSnapshot, errors: List<String>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(SdcGroupNode(kind.title, SdcIcons.app, emptyList()), true)

        val appGroups = when (kind) {
            SdcTabKind.CONTROLLERS -> snapshot.controllers
                .filter { it.value.isNotEmpty() }
                .map { (app, controllers) ->
                    SdcGroupNode(app, SdcIcons.app, hint = "${controllers.size}", children = controllers.map { SdcControllerNode(it) })
                }
            SdcTabKind.MODELS -> snapshot.models
                .groupBy { it.app ?: "?" }
                .map { (app, models) ->
                    SdcGroupNode(app, SdcIcons.app, hint = "${models.size}", children = models.map { SdcModelNode(it) })
                }
        }
        appGroups.forEach { root.add(toTreeNode(it)) }

        errors.forEach { error ->
            root.add(DefaultMutableTreeNode(SdcInfoNode("warning: ${error.take(300)}", SdcIcons.warning), false))
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

    /** Sets the icon per row; DefaultTreeCellRenderer's open/closed/leaf icon fields leak between rows. */
    private class SdcTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject as? SdcTreeNode
            if (node == null) {
                append(value?.toString() ?: "")
                return
            }
            icon = node.icon
            append(node.label, node.textAttributes)
            node.hint?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        }
    }
}
