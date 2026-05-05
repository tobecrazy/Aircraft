package com.young.aircraft.utils

import android.app.Activity
import android.util.Log
import android.webkit.WebView

object DebugTools {
    val isEnabled: Boolean = true

    fun log(msg: String) {
        Log.d("Aircraft", msg)
    }

    fun showOverlay(activity: Activity) = Unit

    fun enableWebViewDebugging() {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
