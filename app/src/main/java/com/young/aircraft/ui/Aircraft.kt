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
    private val bullets = mutableListOf<Float>()
    private var bulletX: Float = 0F
    private var fireCounter: Int = 0
    var jetX: Float = ScreenUtils.getScreenWidth(context).toFloat() / 2 - ScreenUtils.dpToPx(context, 20.0f)
    var jetY: Float = ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)

    companion object {
        const val FIRE_INTERVAL = 5
        const val BULLET_SPEED = 35f
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
        if (jetBitmap != null && bulletBitmap != null) {
            jetBitmap.density = context.resources.displayMetrics.densityDpi
            canvas.drawBitmap(jetBitmap, jetX, jetY, mPaint)

            // Center bullet on jet
            bulletX = jetX + jetBitmap.width / 2f - bulletBitmap.width / 2f

            // Fire new bullet from jet position periodically
            fireCounter++
            if (fireCounter >= FIRE_INTERVAL) {
                fireCounter = 0
                bullets.add(jetY)
            }

            // Update and draw each bullet
            var i = 0
            while (i < bullets.size) {
                bullets[i] -= BULLET_SPEED * speed
                if (bullets[i] < 0) {
                    bullets.removeAt(i)
                } else {
                    canvas.drawBitmap(bulletBitmap, bulletX, bullets[i], mPaint)
                    i++
                }
            }
        }
    }

    fun getBulletX(): Float = bulletX

    fun getBulletYPositions(): List<Float> = bullets

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
        val jetBitmap = BitmapUtils.readBitMap(context, R.drawable.jet_plane)
        val right = jetX + (jetBitmap?.width ?: 0)
        val bottom = jetY + (jetBitmap?.height ?: 0)
        return RectF(jetX, jetY, right, bottom)
    }
}