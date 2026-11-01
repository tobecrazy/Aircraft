package com.young.aircraft.gui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.young.aircraft.ui.GameCoreView

/**
 * Shared helpers for the connected screen tests.
 *
 * Deliberately no ActivityScenario and no ComposeTestRule. Both idle the main
 * thread before returning, and a screen that keeps animating (the star field on
 * the consent/onboarding pages, the game surface) never goes idle on a slow
 * device — launch then blocks for minutes instead of failing. These helpers start
 * the activity directly, track its lifecycle, and poll the live semantics tree,
 * so every page is asserted the same way regardless of what it animates.
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
    val intent = Intent(context, clazz).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return openScreen(intent, clazz, timeoutMs)
}

internal fun <A : Activity> openScreen(intent: Intent, clazz: Class<A>, timeoutMs: Long = 60_000): A {
    ScreenTracker.install()
    ScreenTracker.forget(clazz)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return awaitScreen(clazz, timeoutMs)
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

/** Waits until an activity of [clazz] is RESUMED; used for navigation assertions. */
internal fun awaitResumed(clazz: Class<out Activity>, timeoutMs: Long = 30_000): Activity? {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        ScreenTracker.find(clazz)?.let { return it }
        Thread.sleep(100)
    }
    return null
}

private fun <A : Activity> awaitScreen(clazz: Class<A>, timeoutMs: Long): A {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        val activity = ScreenTracker.find(clazz)
        if (activity != null) return activity as A
        Thread.sleep(100)
    }
    error("${clazz.simpleName} did not resume within ${timeoutMs}ms")
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

internal fun Activity.hasTagNow(tag: String): Boolean {
    var found = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode
            found = root?.hasTag(tag) ?: false
        }
    }
    return found
}

/** Clicks the first node carrying [tag]; false when absent. */
internal fun Activity.clickTag(tag: String): Boolean {
    var clicked = false
    runCatching {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode
            clicked = root?.clickOnTag(tag) ?: false
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
            val root = window.decorView.findSemanticsOwner()?.rootSemanticsNode
            clicked = root?.clickOnText(text) ?: false
        }
    }
    return clicked
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

internal fun View.dfsGameCoreView(): GameCoreView? =
    when (this) {
        is GameCoreView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).dfsGameCoreView() }
        else -> null
    }

internal fun Activity.hasGameCoreView(): Boolean = window.decorView.dfsGameCoreView() != null
