package com.young.aircraft.utils

import android.util.Log

/** Debug logging can be enabled at runtime from the developer tools. */
object AppLog {
    @Volatile
    @JvmField
    var enabled = false

    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.i(tag, msg, tr)
    }

    // Overload, not a trailing `tr`: the message lambda must stay the last parameter so
    // `AppLog.d(tag) { ... }` binds to it instead of to the throwable.
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
}
