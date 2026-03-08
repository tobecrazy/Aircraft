package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

data class Bullet(var x: Float, var y: Float, val originY: Float)

/**
 * Create by Young
 **/
class Aircraft(var context: Context, var speed: Float) : DrawBaseObject(context) {
    private val bullets = mutableListOf<Bullet>()
    private var fireCounter: Int = 0
    var jetX: Float = ScreenUtils.getScreenWidth(context).toFloat() / 2 - ScreenUtils.dpToPx(context, 20.0f)
    var jetY: Float = ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
    private val maxBulletRange: Float = ScreenUtils.getScreenHeight(context).toFloat() * 0.7f

    // Cached rendered dimensions (updated each frame from onDraw)
    @Volatile var renderedJetW: Float = 0f
    @Volatile var renderedJetH: Float = 0f

    companion object {
        const val FIRE_INTERVAL = 2
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
            val screenDensity = context.resources.displayMetrics.densityDpi
            jetBitmap.density = screenDensity
            bulletBitmap.density = screenDensity
            canvas.drawBitmap(jetBitmap, jetX, jetY, mPaint)

            // Compute actual rendered sizes accounting for canvas vs bitmap density scaling
            val scale = if (canvas.density > 0) canvas.density.toFloat() / screenDensity else 1f
            val jetRenderedW = jetBitmap.width * scale
            val jetRenderedH = jetBitmap.height * scale
            val bulletRenderedW = bulletBitmap.width * scale
            val bulletRenderedH = bulletBitmap.height * scale

            // Cache rendered jet size for touch handler and collision detection
            renderedJetW = jetRenderedW
            renderedJetH = jetRenderedH

            // Fire new bullet directly above the center of the aircraft
            fireCounter++
            if (fireCounter >= FIRE_INTERVAL) {
                fireCounter = 0
                val bx = jetX + jetRenderedW / 2f - bulletRenderedW / 2f
                val by = jetY - bulletRenderedH
                bullets.add(Bullet(x = bx, y = by, originY = by))
            }

            // Update and draw each bullet, limit range to 80% of screen height
            var i = 0
            while (i < bullets.size) {
                val bullet = bullets[i]
                bullet.y -= BULLET_SPEED * speed
                val distanceTraveled = bullet.originY - bullet.y
                if (bullet.y < 0 || distanceTraveled > maxBulletRange) {
                    bullets.removeAt(i)
                } else {
                    canvas.drawBitmap(bulletBitmap, bullet.x, bullet.y, mPaint)
                    i++
                }
            }
        }
    }

    fun getRenderedJetSize(): Pair<Float, Float> = Pair(renderedJetW, renderedJetH)

    fun getBullets(): List<Bullet> = bullets

    fun removeBullet(bullet: Bullet) {
        bullets.remove(bullet)
    }

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
        return RectF(jetX, jetY, jetX + renderedJetW, jetY + renderedJetH)
    }
}