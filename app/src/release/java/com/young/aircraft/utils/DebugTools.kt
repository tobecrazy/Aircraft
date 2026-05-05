package com.young.aircraft.utils

import android.app.Activity

object DebugTools {
    val isEnabled: Boolean = false

    fun log(msg: String) = Unit

    fun showOverlay(activity: Activity) = Unit

    fun enableWebViewDebugging() = Unit
}
