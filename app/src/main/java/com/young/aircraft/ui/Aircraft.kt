package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class Aircraft(var context: Context, var speed: Float) : DrawBaseObject(context) {
    var bulletTopY: Float = 0F

    init {
        bulletTopY =
            ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val jetBitmap = BitmapUtils.readBitMap(context, R.drawable.jet_plane)
        val originBitmap = BitmapUtils.readBitMap(context, R.drawable.bullet_up)
        val originBitmap2 = BitmapUtils.readBitMap(context, R.drawable.bullet_down)
        val bulletBitmap = BitmapUtils.resizeBitmap(
            originBitmap,
            ScreenUtils.dpToPx(context, 25.0f),
            ScreenUtils.dpToPx(context, 25.0f)
        )
        if (jetBitmap != null && null != bulletBitmap) {
            jetBitmap.density = context.resources.displayMetrics.densityDpi
            val left = ScreenUtils.getScreenWidth(context).toFloat() / 2 - ScreenUtils.dpToPx(
                context,
                20.0f
            )
            val top =
                ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
            jetBitmap.density = context.resources.displayMetrics.densityDpi
            canvas.drawBitmap(jetBitmap, left, top, mPaint)
            bulletTopY -= 20 * speed
            for (i in 1..100) {
                if (bulletTopY < ScreenUtils.getScreenHeight(context).toFloat()) {
                    canvas.drawBitmap(
                        bulletBitmap,
                        left,
                        bulletTopY + 500 * i - 100 * speed,
                        mPaint
                    )
                }
            }


        }
    }

    override fun updateGame() {

    }
}