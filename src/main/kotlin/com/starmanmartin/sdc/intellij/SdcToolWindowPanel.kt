package com.starmanmartin.sdc.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
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
import javax.swing.event.DocumentEvent
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
    private val searchField = SearchTextField(false)
    private val renderer = SdcTreeRenderer()
    private var snapshot: SdcSnapshot? = null
    private var errors: List<String> = emptyList()

    init {
        val group = DefaultActionGroup().apply { add(RefreshAction()) }
        val toolbar = ActionManager.getInstance().createActionToolbar("SdcToolWindowToolbar", group, true)
        toolbar.targetComponent = this
        searchField.textEditor.emptyText.text = when (kind) {
            SdcTabKind.CONTROLLERS -> "Search controller name or tag"
            SdcTabKind.MODELS -> "Search model name"
        }
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                renderTree()
            }
        })
        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)
            add(searchField, BorderLayout.CENTER)
        }
        add(header, BorderLayout.NORTH)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = renderer
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
        this.snapshot = snapshot
        errors = snapshot.errors.filter { it.startsWith("${kind.command}:") }
        renderTree()
    }

    /** Rebuilds the tree from the last snapshot, filtered by the search field. */
    private fun renderTree() {
        val snapshot = snapshot ?: return
        val query = searchField.text.trim()
        renderer.query = query
        val (root, matches) = buildTree(snapshot, errors, query)
        tree.model = DefaultTreeModel(root)
        expandAll()

        val noun = when (kind) {
            SdcTabKind.CONTROLLERS -> "controllers"
            SdcTabKind.MODELS -> "models"
        }
        val total = when (kind) {
            SdcTabKind.CONTROLLERS -> snapshot.controllers.values.sumOf { it.size }
            SdcTabKind.MODELS -> snapshot.models.size
        }
        val text = if (query.isEmpty()) "Updated $total $noun" else "$matches of $total $noun match \"$query\""
        if (errors.isNotEmpty()) {
            setStatus("$text with warnings (${errors.size})", UIUtil.getErrorForeground())
        } else {
            setStatus(text, UIUtil.getLabelForeground())
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

    /** Returns the tree root and the number of entries matching [query] (all entries when it is empty). */
    private fun buildTree(snapshot: SdcSnapshot, errors: List<String>, query: String): Pair<DefaultMutableTreeNode, Int> {
        val root = DefaultMutableTreeNode(SdcGroupNode(kind.title, SdcIcons.app, emptyList()), true)

        val entriesByApp: Map<String, List<SdcTreeNode>> = when (kind) {
            SdcTabKind.CONTROLLERS -> snapshot.controllers.mapValues { (_, controllers) -> controllers.map { SdcControllerNode(it) } }
            SdcTabKind.MODELS -> snapshot.models.groupBy { it.app ?: "?" }.mapValues { (_, models) -> models.map { SdcModelNode(it) } }
        }
        var matches = 0
        entriesByApp.forEach { (app, entries) ->
            val shown = if (query.isEmpty()) entries else entries.filter { it.matches(query) }
            if (shown.isEmpty()) return@forEach
            matches += shown.size
            val hint = if (shown.size == entries.size) "${entries.size}" else "${shown.size}/${entries.size}"
            root.add(toTreeNode(SdcGroupNode(app, SdcIcons.app, hint = hint, children = shown)))
        }
        if (query.isNotEmpty() && matches == 0) {
            root.add(DefaultMutableTreeNode(SdcInfoNode("Nothing matches \"$query\"", SdcIcons.info), false))
        }

        errors.forEach { error ->
            root.add(DefaultMutableTreeNode(SdcInfoNode("warning: ${error.take(300)}", SdcIcons.warning), false))
        }
        return root to matches
    }

    private fun toTreeNode(node: SdcTreeNode): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node, node.children.isNotEmpty())
        node.children.forEach { treeNode.add(toTreeNode(it)) }
        return treeNode
    }

    private fun expandAll() {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    /**
     * Sets the icon per row (DefaultTreeCellRenderer's open/closed/leaf icon fields leak between rows)
     * and highlights the search [query] in searchable nodes.
     */
    private class SdcTreeRenderer : ColoredTreeCellRenderer() {
        var query: String = ""

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
            val highlight = query.isNotEmpty() && node.searchTerms.isNotEmpty()
            appendHighlighted(node.label, node.textAttributes, highlight)
            node.hint?.let { appendHighlighted("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES, highlight) }
        }

        private fun appendHighlighted(text: String, attributes: SimpleTextAttributes, highlight: Boolean) {
            if (!highlight) {
                append(text, attributes)
                return
            }
            val matchAttributes = attributes.derive(attributes.style or SimpleTextAttributes.STYLE_SEARCH_MATCH, null, null, null)
            var start = 0
            while (true) {
                val index = text.indexOf(query, start, ignoreCase = true)
                if (index < 0) break
                if (index > start) append(text.substring(start, index), attributes)
                append(text.substring(index, index + query.length), matchAttributes)
                start = index + query.length
            }
            if (start < text.length) append(text.substring(start), attributes)
        }
    }
}
