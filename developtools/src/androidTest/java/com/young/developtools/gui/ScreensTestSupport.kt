package com.young.developtools.gui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Shared helpers for the connected developtools screen tests.
 *
 * Deliberately no ActivityScenario and no ComposeTestRule: both idle the main
 * thread before returning, and tool screens that animate or load asynchronously
 * can keep it busy long enough for that to block instead of time out. These
 * helpers start the activity directly, track its lifecycle, and poll the live
 * semantics tree.
 */

private object ScreenTracker : Application.ActivityLifecycleCallbacks {
    private val resumed = java.util.concurrent.ConcurrentHashMap<Class<out Activity>, Activity>()
    private var installed = false

    fun install() {
        if (installed) return
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        application.registerActivityLifecycleCallbacks(this)
        installed = true
    }

    fun find(clazz: Class<out Activity>): Activity? = resumed[clazz]

    fun forget(clazz: Class<out Activity>) {
        resumed.remove(clazz)
    }

    override fun onActivityResumed(activity: Activity) {
        resumed[activity.javaClass] = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        resumed.remove(activity.javaClass, activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

internal fun scenarioString(res: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

/** Opens [clazz] in its own task and waits for it to resume; no idling involved. */
internal fun <A : Activity> openScreen(clazz: Class<A>, timeoutMs: Long = 60_000): A {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return openScreen(Intent(context, clazz), clazz, timeoutMs)
}

internal fun <A : Activity> openScreen(intent: Intent, clazz: Class<A>, timeoutMs: Long = 60_000): A {
    ScreenTracker.install()
    ScreenTracker.forget(clazz)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        val activity = ScreenTracker.find(clazz)
        if (activity != null) return activity as A
        Thread.sleep(100)
    }
    error("${clazz.simpleName} did not resume within ${timeoutMs}ms")
}

/** Runs [block] with an open screen and always finishes it afterwards. */
internal inline fun <A : Activity> withScreen(clazz: Class<A>, block: (A) -> Unit) {
    val screen = openScreen(clazz)
    try {
        block(screen)
    } finally {
        screen.closeScreen()
    }
}

internal inline fun <A : Activity> withScreen(intent: Intent, clazz: Class<A>, block: (A) -> Unit) {
    val screen = openScreen(intent, clazz)
    try {
        block(screen)
    } finally {
        screen.closeScreen()
    }
}

internal fun Activity.closeScreen(timeoutMs: Long = 15_000) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    runCatching { instrumentation.runOnMainSync { if (!isFinishing) finish() } }
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (ScreenTracker.find(javaClass) !== this) return
        Thread.sleep(100)
    }
}

/** Polls until [text] is present in this window; false after [timeoutMs]. */
internal fun Activity.waitForText(text: String, timeoutMs: Long = 30_000): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (hasTextNow(text)) return true
        Thread.sleep(200)
    }
    return false
}

internal fun Activity.waitForText(res: Int, timeoutMs: Long = 30_000): Boolean =
    waitForText(scenarioString(res), timeoutMs)

/** Clicks the first node carrying [tag]; false when absent. */
internal fun Activity.clickTag(tag: String): Boolean {
    var clicked = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode ?: return@runOnMainSync
            val target = findAllNodes(root).firstOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == tag
            } ?: return@runOnMainSync
            target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            clicked = true
        }
    }
    return clicked
}

/** Clicks the first clickable node whose text contains [res]; false when absent. */
internal fun Activity.clickText(res: Int): Boolean = clickText(scenarioString(res))

internal fun Activity.clickText(text: String): Boolean {
    var clicked = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode ?: return@runOnMainSync
            val target = findAllNodes(root)
                .filter { it.displayText()?.contains(text) == true }
                .firstOrNull { it.config.getOrNull(SemanticsActions.OnClick) != null }
                ?: return@runOnMainSync
            target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            clicked = true
        }
    }
    return clicked
}

/** Types [value] into the field carrying [tag]; false when absent or read-only. */
internal fun Activity.setText(tag: String, value: String): Boolean {
    var typed = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode ?: return@runOnMainSync
            val target = findAllNodes(root).firstOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == tag
            } ?: return@runOnMainSync
            val action = target.config.getOrNull(SemanticsActions.SetText)?.action ?: return@runOnMainSync
            action(AnnotatedString(value))
            typed = true
        }
    }
    return typed
}

private fun Activity.hasTextNow(text: String): Boolean {
    var found = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode
            found = root != null && findAllNodes(root).any { node ->
                node.displayText()?.contains(text) == true
            }
        }
    }
    return found
}

// AndroidComposeView is internal; reach its Owner.semanticsOwner reflectively.
private val composeRuntimeClass by lazy { Class.forName("androidx.compose.ui.platform.AndroidComposeView") }

private fun View.findSemanticsOwner(): SemanticsOwner? {
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

private fun findAllNodes(node: SemanticsNode): List<SemanticsNode> =
    listOf(node) + node.children.flatMap(::findAllNodes)

private fun SemanticsNode.displayText(): String? {
    config.getOrNull(SemanticsProperties.Text)?.let { texts ->
        if (texts.isNotEmpty()) return texts.joinToString("") { it.toString() }
    }
    config.getOrNull(SemanticsProperties.ContentDescription)?.let { descriptions ->
        if (descriptions.isNotEmpty()) return descriptions.joinToString(" ") { it.toString() }
    }
    return null
}
