package com.young.aircraft.gui

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Semantics-tree traversal for Compose content living OUTSIDE a ComposeTestRule:
 * dialogs/bottom sheets hosted in classic windows, or activities launched via
 * manual ActivityScenario (Robolectric cannot resolve manifest-less hosts).
 */

// AndroidComposeView is internal; reach its Owner.semanticsOwner reflectively.
private val composeRuntimeClass by lazy { Class.forName("androidx.compose.ui.platform.AndroidComposeView") }

fun View.findSemanticsOwner(): SemanticsOwner? {
    if (composeRuntimeClass.isInstance(this)) {
        val getter = composeRuntimeClass.getMethod("getSemanticsOwner")
        getter.isAccessible = true
        return getter.invoke(this) as? SemanticsOwner
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findSemanticsOwner()?.let { return it }
        }
    }
    return null
}

/** Depth-first flatten of the semantics tree rooted at [node]. */
fun findAllNodes(node: SemanticsNode): List<SemanticsNode> =
    listOf(node) + node.children.flatMap(::findAllNodes)

/** Visible text (or content description fallback) carried by a semantics node. */
/**
 * All text a node carries. A text field reports its value as EditableText while
 * its merged children (label, supporting text) stay in Text, so both are needed:
 * returning the field value alone would hide the validation message.
 */
fun SemanticsNode.displayText(): String? {
    val parts = buildList {
        config.getOrNull(SemanticsProperties.EditableText)?.text?.let { if (it.isNotEmpty()) add(it) }
        config.getOrNull(SemanticsProperties.Text)?.let { texts ->
            if (texts.isNotEmpty()) add(texts.joinToString("") { it.toString() })
        }
        config.getOrNull(SemanticsProperties.ContentDescription)?.let { descs ->
            if (descs.isNotEmpty()) add(descs.joinToString(" "))
        }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" ")
}

/**
 * Invokes the click action of the first node whose text contains [text]; returns
 * false when absent. Clickable rows merge their children's text, so an exact
 * match would miss the row that actually carries the click action.
 */
fun SemanticsNode.clickOnText(text: String): Boolean {
    val target = findAllNodes(this)
        .filter { it.displayText()?.contains(text) == true }
        .firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null } ?: return false
    target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
    return true
}

/** Invokes the click action of the first node carrying [tag]; returns false when absent. */
fun SemanticsNode.clickOnTag(tag: String): Boolean {
    val target = findAllNodes(this).firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    } ?: return false
    val click = target.config.getOrNull(SemanticsActions.OnClick)?.action ?: return false
    click()
    return true
}

/** True when any node in this subtree carries [tag]. */
fun SemanticsNode.hasTag(tag: String): Boolean =
    findAllNodes(this).any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
