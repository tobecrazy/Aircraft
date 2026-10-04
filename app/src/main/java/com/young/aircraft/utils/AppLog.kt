package com.young.aircraft.utils

import android.util.Log

/** Debug logging can be enabled at runtime from the developer tools. */
object AppLog {
    @Volatile
    @JvmField
    var enabled = false

    fun i(tag: String, msg: String) {
        if (enabled) Log.i(tag, msg)
    }

    fun d(tag: String, msg: () -> String) {
        if (enabled) Log.d(tag, msg())
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String) {
        if (enabled) Log.w(tag, msg)
    }

    fun v(tag: String, msg: String) {
        if (enabled) Log.v(tag, msg)
    }
}
