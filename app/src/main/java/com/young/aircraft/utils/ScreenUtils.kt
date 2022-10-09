package com.young.aircraft.utils

import android.app.Activity
import android.content.Context
import android.os.Build

/**
 * Create by Young
 **/
object ScreenUtils {
    @Synchronized
    fun getScreenWidth(activity: Activity, context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels
        }
    }

    @Synchronized
    fun getScreenHeight(activity: Activity, context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.height()
        } else {
            val metrics = context.resources.displayMetrics
            metrics.heightPixels
        }
    }
}