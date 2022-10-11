package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class DrawBackground(var context: Context) : DrawBaseObject(context) {
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val bitmap = BitmapUtils.readBitMap(context, R.drawable.background)
        if (bitmap != null) {
            val rectF: RectF = RectF(
                /* left = */ 0F,
                /* top = */ 0F,
                /* right = */ ScreenUtils.getScreenWidth(context).toFloat(),
                /* bottom = */ ScreenUtils.getScreenHeight(context).toFloat()
            )
            bitmap.density = context.resources.displayMetrics.densityDpi
            canvas.drawBitmap(bitmap, null, rectF, null)
        }
    }

    override fun updateGame() {

    }
}