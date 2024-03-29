package com.young.aircraft.utils

import android.app.Activity
import android.content.Context
import android.os.Build

/**
 * Create by Young
 **/
object ScreenUtils {
    @Synchronized
    fun getScreenWidth(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (context as Activity).windowManager.currentWindowMetrics.bounds.width()
        } else {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels
        }
    }

    @Synchronized
    fun getScreenHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (context as Activity).windowManager.currentWindowMetrics.bounds.height()
        } else {
            val metrics = context.resources.displayMetrics
            metrics.heightPixels
        }
    }

    @Synchronized
    fun sp2px(context: Context, spValue: Float): Float {
        return context.resources.displayMetrics.scaledDensity * spValue + 0.5f
    }


    @Synchronized
    fun dpToPx(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    @Synchronized
    fun pxToDp(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }
}
