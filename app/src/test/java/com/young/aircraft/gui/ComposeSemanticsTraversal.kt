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
fun SemanticsNode.displayText(): String? {
    config.getOrNull(SemanticsProperties.Text)?.let { texts ->
        if (texts.isNotEmpty()) return texts.joinToString("") { it.toString() }
    }
    config.getOrNull(SemanticsProperties.ContentDescription)?.let { descs ->
        if (descs.isNotEmpty()) return descs.joinToString(" ") { it.toString() }
    }
    return null
}

/** Invokes the click action of the first node matching [text]; returns false when absent. */
fun SemanticsNode.clickOnText(text: String): Boolean {
    val target = findAllNodes(this).firstOrNull { it.displayText() == text } ?: return false
    val click = target.config.getOrNull(SemanticsActions.OnClick)?.action ?: return false
    click()
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
