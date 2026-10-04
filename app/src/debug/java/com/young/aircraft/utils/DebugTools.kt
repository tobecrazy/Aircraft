package com.young.aircraft.utils

import android.app.Activity
import android.webkit.WebView

object DebugTools {
    var isEnabled: Boolean = true

    fun log(msg: String) {
        AppLog.d("Aircraft", { msg })
    }

    fun showOverlay(activity: Activity) = Unit

    fun enableWebViewDebugging() {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
