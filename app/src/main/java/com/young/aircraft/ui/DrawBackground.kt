package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class DrawBackground(var context: Context, var speed: Float) : DrawBaseObject(context) {

    //background Top/Bottom
    var mTopY: Float = 0F
    var mBottomY: Float = 0F

    init {
        mTopY = -ScreenUtils.getScreenHeight(context).toFloat()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val originalBitmap = BitmapUtils.readBitMap(context, R.drawable.background)
        val width = ScreenUtils.getScreenWidth(context)
        val height = ScreenUtils.getScreenHeight(context)
        val bitmap = BitmapUtils.resizeBitmap(originalBitmap, width, height)
        if (bitmap != null) {
            canvas.density = bitmap.density
//            val rectF: RectF = RectF(
//                /* left = */ 0F,
//                /* top = */ 0F,
//                /* right = */ ScreenUtils.getScreenWidth(context).toFloat(),
//                /* bottom = */ ScreenUtils.getScreenHeight(context).toFloat()
//            )
//            bitmap.density = context.resources.displayMetrics.densityDpi
//            canvas.drawBitmap(bitmap, null, rectF, mPaint)
            mTopY += 10F * speed
            mBottomY += 10F * speed
            if (mTopY > height || mBottomY > height) {
                mTopY = 0F
                mBottomY = -ScreenUtils.getScreenHeight(context).toFloat()
            }
            canvas.drawBitmap(bitmap, 0F, mTopY, mPaint);
            canvas.drawBitmap(bitmap, 0F, mBottomY, mPaint);
        }
    }

    override fun updateGame() {

    }
}