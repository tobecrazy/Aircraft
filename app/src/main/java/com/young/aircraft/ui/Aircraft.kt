package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class Aircraft(var context: Context, var speed: Float) : DrawBaseObject(context) {
    var bulletTopY: Float = 0F
    private var lastBulletX: Float = 0F
    private val lastBulletYPositions = mutableListOf<Float>()

    init {
        bulletTopY =
            ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val jetBitmap = BitmapUtils.readBitMap(context, R.drawable.jet_plane)
        val originBitmap = BitmapUtils.readBitMap(context, R.drawable.bullet_up)
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
            lastBulletX = left
            lastBulletYPositions.clear()
            for (i in 1..100) {
                val by = bulletTopY + 500 * i - 100 * speed
                if (by >= 0 && by < ScreenUtils.getScreenHeight(context).toFloat()) {
                    canvas.drawBitmap(bulletBitmap, left, by, mPaint)
                    lastBulletYPositions.add(by)
                }
            }
        }
    }

    fun getBulletX(): Float = lastBulletX

    fun getBulletYPositions(): List<Float> = lastBulletYPositions

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        val left = x
        val top = y
        val right = x + bitmap.width
        val bottom = y + bitmap.height
        return RectF(left, top, right, bottom)
    }

    fun getBounds(): RectF {
        val left = ScreenUtils.getScreenWidth(context).toFloat() / 2 - ScreenUtils.dpToPx(context, 20.0f)
        val top = ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
        val jetBitmap = BitmapUtils.readBitMap(context, R.drawable.jet_plane)
        val right = left + (jetBitmap?.width ?: 0)
        val bottom = top + (jetBitmap?.height ?: 0)
        return RectF(left, top, right, bottom)
    }
}