package com.young.developtools.utils

import android.util.Log
import com.young.developtools.DevTools

/**
 * Module-local logging kill-switch. Same shape and call conventions as the app's AppLog
 * (which the game engine keeps using): lambda messages stay in trailing position, never
 * add a trailing `tr` parameter beside them.
 *
 * Seeded from the module BuildConfig.DEBUG at startup (see DevLogInit); the assistant
 * log toggle persists via LogSettings and fans out to the app-wide AppLog through
 * [DevTools.onLogEnabledChanged] when the host wires it.
 */
object DevLog {
    @Volatile
    @JvmField
    var enabled = false

    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.i(tag, msg, tr)
    }

    // Overload, not a trailing `tr`: the message lambda must stay the last parameter so
    // `DevLog.d(tag) { ... }` binds to it instead of to the throwable.
    fun d(tag: String, msg: () -> String) {
        if (enabled) Log.d(tag, msg())
    }

    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.d(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.w(tag, msg, tr)
    }

    fun v(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.v(tag, msg, tr)
    }

    /** Single choke point for external writes; forwards to the host hook when wired. */
    fun setEnabledAndForward(value: Boolean) {
        enabled = value
        runCatching { DevTools.onLogEnabledChanged?.invoke(value) }
    }
}
