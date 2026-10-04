package com.starmanmartin.sdc.intellij.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.extension

object SdcIcons {
    val root: Icon = AllIcons.Nodes.Folder
    val app: Icon = AllIcons.Nodes.Module
    val model: Icon = AllIcons.Nodes.Class
    val controller: Icon = AllIcons.Nodes.Class
    val form: Icon = AllIcons.Nodes.Class
    val tag: Icon = AllIcons.Nodes.Tag
    val url: Icon = AllIcons.General.Web
    val python: Icon = AllIcons.FileTypes.Text
    val js: Icon = AllIcons.FileTypes.JavaScript
    val scss: Icon = AllIcons.FileTypes.Css
    val html: Icon = AllIcons.FileTypes.Html
    val info: Icon = AllIcons.General.Information
}

fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

private fun lineSuffix(line: Int?): String = if (line != null && line > 0) " (line $line)" else ""

private fun fileIcon(path: String): Icon = when (Path.of(path).extension.lowercase()) {
    "js" -> SdcIcons.js
    "scss", "css" -> SdcIcons.scss
    "html" -> SdcIcons.html
    "py" -> SdcIcons.python
    else -> SdcIcons.info
}

/** Base class for nodes shown in the SDC tool window tree. */
abstract class SdcTreeNode(val label: String, val icon: Icon) {
    open val children: List<SdcTreeNode> = emptyList()

    /** Called on double click. Return true if the node handled the action. */
    open fun onDoubleClick(project: Project): Boolean = false
}

/** Static information entry (tag name, URL, ...); not clickable. */
class SdcInfoNode(label: String, icon: Icon = SdcIcons.info) : SdcTreeNode(label, icon)

/** Opens the referenced file (at [line], if given) in the editor on double click. */
class SdcFileNode(
    val path: String,
    val line: Int? = null,
    label: String = fileName(path) + lineSuffix(line),
    icon: Icon = fileIcon(path),
) : SdcTreeNode(label, icon) {

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

/** Pure grouping node (root, "Controllers", "Models", per-app groups). */
class SdcGroupNode(label: String, icon: Icon, override val children: List<SdcTreeNode>) : SdcTreeNode(label, icon)

/** One SDC controller: tag name, URL, server view and client asset files. */
class SdcControllerNode(info: com.starmanmartin.sdc.intellij.SdcControllerInfo) :
    SdcTreeNode(info.name ?: "controller", SdcIcons.controller) {

    override val children: List<SdcTreeNode> = run {
        val items = mutableListOf<SdcTreeNode>()
        info.tagName?.let { items.add(SdcInfoNode("tag: <$it>", SdcIcons.tag)) }
        info.url?.let { items.add(SdcInfoNode("url: $it", SdcIcons.url)) }
        info.sdcViewFile?.let {
            items.add(SdcFileNode(it, info.sdcViewFileNumber, "SDCView: ${fileName(it)}${lineSuffix(info.sdcViewFileNumber)}"))
        }
        info.js?.let { items.add(SdcFileNode(it, null, "JS: ${fileName(it)}")) }
        info.scss?.let { items.add(SdcFileNode(it, null, "SCSS: ${fileName(it)}")) }
        info.html?.let { items.add(SdcFileNode(it, null, "HTML: ${fileName(it)}")) }
        items
    }
}

/** One SdcModel: python model, forms and list/detail/form templates. */
class SdcModelNode(info: com.starmanmartin.sdc.intellij.SdcModelInfo) :
    SdcTreeNode(info.name ?: "model", SdcIcons.model) {

    override val children: List<SdcTreeNode> = run {
        val items = mutableListOf<SdcTreeNode>()
        info.modelFile?.let {
            items.add(SdcFileNode(it, info.modelFileLine, "Model: ${fileName(it)}${lineSuffix(info.modelFileLine)}"))
        }
        info.createForm?.let { form ->
            form.file?.let {
                items.add(SdcFileNode(it, form.line, "Create form: ${form.className ?: fileName(it)}${lineSuffix(form.line)}"))
            }
        }
        info.editForm?.let { form ->
            form.file?.let {
                items.add(SdcFileNode(it, form.line, "Edit form: ${form.className ?: fileName(it)}${lineSuffix(form.line)}"))
            }
        }
        info.htmlDetailTemplate?.let { items.add(SdcFileNode(it, null, "Detail template: ${fileName(it)}")) }
        info.htmlListTemplate?.let { items.add(SdcFileNode(it, null, "List template: ${fileName(it)}")) }
        info.htmlFormTemplate?.let { items.add(SdcFileNode(it, null, "Form template: ${fileName(it)}")) }
        items
    }
}
