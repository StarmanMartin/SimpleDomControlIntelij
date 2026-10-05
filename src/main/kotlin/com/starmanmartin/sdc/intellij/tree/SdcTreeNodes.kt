package com.starmanmartin.sdc.intellij.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.SimpleTextAttributes
import java.nio.file.Path
import javax.swing.Icon

object SdcIcons {
    val app: Icon = AllIcons.Nodes.Module
    val model: Icon = AllIcons.Nodes.Record
    val controller: Icon = AllIcons.Nodes.Class
    val form: Icon = AllIcons.FileTypes.UiForm
    val tag: Icon = AllIcons.Nodes.Tag
    val url: Icon = AllIcons.General.Web
    val file: Icon = AllIcons.FileTypes.Text
    val info: Icon = AllIcons.General.Information
    val warning: Icon = AllIcons.General.Warning
    val error: Icon = AllIcons.General.Error
}

fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

private fun fileHint(path: String, line: Int?): String =
    if (line != null && line > 0) "${fileName(path)}:$line" else fileName(path)

/** Icon the IDE uses for this file type (e.g. the Python icon in PyCharm), falling back to a plain file icon. */
private fun fileIcon(path: String): Icon {
    val type = FileTypeManager.getInstance().getFileTypeByFileName(fileName(path))
    return if (type is UnknownFileType) SdcIcons.file else type.icon ?: SdcIcons.file
}

/** Base class for nodes shown in the SDC tool window tree. [hint] is rendered grayed after [label]. */
abstract class SdcTreeNode(val label: String, val icon: Icon, val hint: String? = null) {
    open val children: List<SdcTreeNode> = emptyList()
    open val textAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES

    /** Texts the tool window search matches against; nodes without terms are not searchable. */
    open val searchTerms: List<String> = emptyList()

    fun matches(query: String): Boolean = searchTerms.any { it.contains(query, ignoreCase = true) }

    /** Called on double click. Return true if the node handled the action. */
    open fun onDoubleClick(project: Project): Boolean = false
}

/** Static information entry (tag name, URL, ...); not clickable. */
class SdcInfoNode(label: String, icon: Icon = SdcIcons.info, hint: String? = null) : SdcTreeNode(label, icon, hint)

/** Opens the referenced file (at [line], if given) in the editor on double click. */
class SdcFileNode(
    val path: String,
    val line: Int? = null,
    label: String = fileName(path),
    icon: Icon = fileIcon(path),
) : SdcTreeNode(label, icon, if (label == fileName(path)) null else fileHint(path, line)) {

    override fun onDoubleClick(project: Project): Boolean {
        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(path))
            ?: return false
        val descriptor = if (line != null && line > 0) {
            OpenFileDescriptor(project, virtualFile, line - 1, 0)
        } else {
            OpenFileDescriptor(project, virtualFile)
        }
        descriptor.navigate(true)
        return true
    }
}

/** Pure grouping node (root, per-app groups). */
class SdcGroupNode(
    label: String,
    icon: Icon,
    override val children: List<SdcTreeNode>,
    hint: String? = null,
) : SdcTreeNode(label, icon, hint) {
    override val textAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
}

/** One SDC controller: tag name, URL, server view and client asset files. */
class SdcControllerNode(info: com.starmanmartin.sdc.intellij.SdcControllerInfo) :
    SdcTreeNode(info.name ?: "controller", SdcIcons.controller, info.tagName?.let { "<$it>" }) {

    override val searchTerms: List<String> = listOfNotNull(info.name, info.tagName)

    override val children: List<SdcTreeNode> = run {
        val items = mutableListOf<SdcTreeNode>()
        info.tagName?.let { items.add(SdcInfoNode("tag", SdcIcons.tag, "<$it>")) }
        info.url?.let { items.add(SdcInfoNode("url", SdcIcons.url, it)) }
        info.sdcViewFile?.let { items.add(SdcFileNode(it, info.sdcViewFileNumber, "SDCView")) }
        info.js?.let { items.add(SdcFileNode(it, null, "JS")) }
        info.scss?.let { items.add(SdcFileNode(it, null, "SCSS")) }
        info.html?.let { items.add(SdcFileNode(it, null, "HTML")) }
        items
    }
}

/** One SdcModel: python model, forms and list/detail/form templates. */
class SdcModelNode(info: com.starmanmartin.sdc.intellij.SdcModelInfo) :
    SdcTreeNode(info.name ?: "model", SdcIcons.model) {

    override val searchTerms: List<String> = listOfNotNull(info.name)

    override val children: List<SdcTreeNode> = run {
        val items = mutableListOf<SdcTreeNode>()
        info.modelFile?.let { items.add(SdcFileNode(it, info.modelFileLine, "Model")) }
        info.createForm?.let { form ->
            form.file?.let { items.add(SdcFileNode(it, form.line, "Create form: ${form.className ?: fileName(it)}", SdcIcons.form)) }
        }
        info.editForm?.let { form ->
            form.file?.let { items.add(SdcFileNode(it, form.line, "Edit form: ${form.className ?: fileName(it)}", SdcIcons.form)) }
        }
        info.htmlDetailTemplate?.let { items.add(SdcFileNode(it, null, "Detail template")) }
        info.htmlListTemplate?.let { items.add(SdcFileNode(it, null, "List template")) }
        info.htmlFormTemplate?.let { items.add(SdcFileNode(it, null, "Form template")) }
        items
    }
}
