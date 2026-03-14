package com.young.aircraft.utils

import android.app.Activity
import android.content.Context
import android.util.TypedValue

/**
 * Create by Young
 **/
object ScreenUtils {
    @Synchronized
    fun getScreenWidth(context: Context): Int {
        return (context as Activity).windowManager.currentWindowMetrics.bounds.width()
    }

    @Synchronized
    fun getScreenHeight(context: Context): Int {
        return (context as Activity).windowManager.currentWindowMetrics.bounds.height()
    }

    @Synchronized
    fun sp2px(context: Context, spValue: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue, context.resources.displayMetrics)
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
