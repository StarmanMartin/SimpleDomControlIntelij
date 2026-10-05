package com.starmanmartin.sdc.intellij

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.htmlInspections.HtmlUnknownElementInspection
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.starmanmartin.sdc.intellij.settings.SdcSettingsState

/**
 * Registers SDC controller tags (`<borrow-btn>`) and the client's `sdc_<event>` attributes as custom
 * entries of the "Unknown HTML tag" / "Unknown HTML attribute" inspections of the current profile
 * (the same lists the "Add to custom HTML tags/attributes" quick fix writes to).
 *
 * Entries added here are remembered in [SdcSettingsState] so stale controller tags can be removed
 * later without touching entries the user added by hand. Call on the EDT.
 */
object SdcHtmlInspectionSync {

    private const val UNKNOWN_TAG = "HtmlUnknownTag"
    private const val UNKNOWN_ATTRIBUTE = "HtmlUnknownAttribute"

    /**
     * The client listens on every standard `window.on*` event (`STD_EVENT_LIST` in
     * SimpleDomControlClient/src/simpleDomControl/sdc_dom_events.js) and reads `sdc_<event>` attributes.
     */
    private val DOM_EVENTS = listOf(
        "abort", "afterprint", "animationcancel", "animationend", "animationiteration", "animationstart",
        "auxclick", "beforeinput", "beforematch", "beforeprint", "beforetoggle", "blur", "cancel",
        "canplay", "canplaythrough", "change", "click", "close", "contentvisibilityautostatechange",
        "contextlost", "contextmenu", "contextrestored", "copy", "cuechange", "cut", "dblclick",
        "devicemotion", "deviceorientation", "deviceorientationabsolute", "drag", "dragend", "dragenter",
        "dragleave", "dragover", "dragstart", "drop", "durationchange", "emptied", "ended", "error", "focus",
        "formdata", "gotpointercapture", "hashchange", "input", "invalid", "keydown", "keypress", "keyup",
        "languagechange", "load", "loadeddata", "loadedmetadata", "loadstart", "lostpointercapture",
        "message", "messageerror", "mousedown", "mouseenter", "mouseleave", "mousemove", "mouseout",
        "mouseover", "mouseup", "offline", "online", "pagehide", "pagereveal", "pageshow", "pageswap",
        "paste", "pause", "play", "playing", "pointercancel", "pointerdown", "pointerenter", "pointerleave",
        "pointermove", "pointerout", "pointerover", "pointerrawupdate", "pointerup", "popstate", "progress",
        "ratechange", "rejectionhandled", "reset", "resize", "scroll", "scrollend", "scrollsnapchange",
        "scrollsnapchanging", "search", "securitypolicyviolation", "seeked", "seeking", "select",
        "selectionchange", "selectstart", "slotchange", "stalled", "storage", "submit", "suspend",
        "timeupdate", "toggle", "transitioncancel", "transitionend", "transitionrun", "transitionstart",
        "unhandledrejection", "volumechange", "waiting", "wheel",
    )

    val eventAttributes: List<String> = DOM_EVENTS.map { "sdc_$it" }

    private class Update(val values: List<String>, val managed: List<String>)

    fun sync(project: Project, snapshot: SdcSnapshot) {
        if (project.isDisposed) return
        val settings = SdcSettingsState.getInstance(project)
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile

        // Without controller data (command failed) keep the registered tags instead of removing them all.
        val controllersLoaded = snapshot.errors.none { it.startsWith("${SdcTabKind.CONTROLLERS.command}:") }
        val tagUpdate = if (controllersLoaded) {
            val tags = snapshot.controllers.values.flatten()
                .mapNotNull { it.tagName?.trim()?.lowercase() }
                .filter { it.isNotEmpty() }
            planUpdate(profile, project, UNKNOWN_TAG, tags, settings.managedHtmlTags)
        } else {
            null
        }
        val attributeUpdate = planUpdate(profile, project, UNKNOWN_ATTRIBUTE, eventAttributes, settings.managedHtmlAttributes)
        if (tagUpdate == null && attributeUpdate == null) return

        profile.modifyProfile { model ->
            tagUpdate?.let { applyUpdate(model, project, UNKNOWN_TAG, it) }
            attributeUpdate?.let { applyUpdate(model, project, UNKNOWN_ATTRIBUTE, it) }
        }
        tagUpdate?.let { settings.managedHtmlTags = it.managed.toMutableList() }
        attributeUpdate?.let { settings.managedHtmlAttributes = it.managed.toMutableList() }
    }

    private fun tool(profile: InspectionProfileImpl, project: Project, shortName: String): HtmlUnknownElementInspection? =
        profile.getInspectionTool(shortName, project)?.tool as? HtmlUnknownElementInspection

    /** Returns null when the inspection already has exactly the wanted entries. */
    private fun planUpdate(
        profile: InspectionProfileImpl,
        project: Project,
        shortName: String,
        wanted: Collection<String>,
        managed: Collection<String>,
    ): Update? {
        val tool = tool(profile, project, shortName) ?: return null
        val current = tool.myValues?.toList() ?: emptyList()
        val wantedSet = wanted.toSet()
        val stale = managed.toSet() - wantedSet
        val missing = wantedSet - current.toSet()
        val values = current.filter { it !in stale } + missing.sorted()
        // Entries the user had added before stay unmanaged, so they are never removed by the sync.
        val newManaged = (managed.filter { it in wantedSet } + missing).distinct().sorted()
        if (values == current && tool.myCustomValuesEnabled && newManaged == managed.toList()) return null
        return Update(values, newManaged)
    }

    private fun applyUpdate(profile: InspectionProfileImpl, project: Project, shortName: String, update: Update) {
        val tool = tool(profile, project, shortName) ?: return
        tool.myCustomValuesEnabled = true
        tool.myValues.clear()
        tool.myValues.addAll(update.values)
    }
}
