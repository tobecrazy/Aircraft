package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

data class Bullet(var x: Float, var y: Float, val originY: Float)

/**
 * Create by Young
 **/
class Aircraft(
    var context: Context,
    var speed: Float,
    private val jetPlaneResId: Int = R.drawable.jet_plane_2,
    private val fireRateMultiplier: Float = 1.0f
) : DrawBaseObject(context) {
    private val bullets = mutableListOf<Bullet>()
    private var fireAccumulator: Float = 0f
    private val screenDensity = context.resources.displayMetrics.densityDpi
    private val jetBitmap: Bitmap? = BitmapUtils.readBitMap(context, jetPlaneResId)?.also {
        it.density = screenDensity
    }
    private val bulletBitmap: Bitmap? = BitmapUtils.resizeBitmap(
        BitmapUtils.readBitMap(context, R.drawable.bullet_up),
        ScreenUtils.dpToPx(context, 40.0f),
        ScreenUtils.dpToPx(context, 40.0f)
    )?.also {
        it.density = screenDensity
    }
    val bulletWidthPx: Float = bulletBitmap?.width?.toFloat() ?: 0f
    val bulletHeightPx: Float = bulletBitmap?.height?.toFloat() ?: 0f
    var jetX: Float =
        ScreenUtils.getScreenWidth(context).toFloat() / 2 - ScreenUtils.dpToPx(context, 20.0f)
    var jetY: Float =
        ScreenUtils.getScreenHeight(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
    private val maxBulletRange: Float = ScreenUtils.getScreenHeight(context).toFloat() * 0.7f

    // Hit flash state
    var hitTimeMs: Long = 0L

    // Shield invincibility state
    var shieldEndTimeMs: Long = 0L
    private var shieldBlinkCounter: Int = 0

    // Freeze state (when true, bullets don't fire or move)
    var frozen: Boolean = false

    // Paint with white tint for hit flash effect
    private val hitFlashPaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, 255f,  // R
                    0f, 0f, 0f, 0f, 255f,  // G
                    0f, 0f, 0f, 0f, 255f,  // B
                    0f, 0f, 0f, 1f, 0f     // A
                )
            )
        )
    }

    // Cached rendered dimensions (updated each frame from onDraw)
    @Volatile
    var renderedJetW: Float = 0f
    @Volatile
    var renderedJetH: Float = 0f

    companion object {
        val JET_PLANES = intArrayOf(
            R.drawable.jet_plane_2,
            R.drawable.jet_plane_3,
            R.drawable.jet_plane_4,
            R.drawable.jet_plane_1
        )
        const val FIRE_INTERVAL = 4
        const val BULLET_SPEED = 35f
        const val HIT_FLASH_DURATION_MS = 200L
        const val SHIELD_DURATION_MS = 10_000L
        const val SHIELD_BLINK_INTERVAL = 4  // toggle visibility every 4 frames
    }

    fun isShielded(): Boolean = System.currentTimeMillis() < shieldEndTimeMs

    fun activateShield() {
        shieldEndTimeMs = System.currentTimeMillis() + SHIELD_DURATION_MS
    }

    override fun onDraw(canvas: Canvas) {
        if (jetBitmap != null && bulletBitmap != null) {
            // Determine paint: hit flash > shield blink > normal
            val drawPaint = if (System.currentTimeMillis() - hitTimeMs < HIT_FLASH_DURATION_MS) {
                hitFlashPaint
            } else if (isShielded()) {
                shieldBlinkCounter++
                val cycle = shieldBlinkCounter / SHIELD_BLINK_INTERVAL
                if (cycle % 2 == 0) mPaint else null  // null = skip drawing (blink off)
            } else {
                shieldBlinkCounter = 0
                mPaint
            }

            if (drawPaint != null) {
                canvas.drawBitmap(jetBitmap, jetX, jetY, drawPaint)
            }

            // Compute actual rendered sizes accounting for canvas vs bitmap density scaling
            val scale = if (canvas.density > 0) canvas.density.toFloat() / screenDensity else 1f
            val jetRenderedW = jetBitmap.width * scale
            val jetRenderedH = jetBitmap.height * scale
            val bulletRenderedW = bulletBitmap.width * scale
            val bulletRenderedH = bulletBitmap.height * scale

            // Cache rendered jet size for touch handler and collision detection
            renderedJetW = jetRenderedW
            renderedJetH = jetRenderedH

            // Fire new bullet directly above the center of the aircraft (only when not frozen)
            if (!frozen) {
                fireAccumulator += fireRateMultiplier
                if (fireAccumulator >= FIRE_INTERVAL) {
                    fireAccumulator -= FIRE_INTERVAL
                    val bx = jetX + jetRenderedW / 2f - bulletRenderedW / 2f
                    val by = jetY - bulletRenderedH
                    bullets.add(Bullet(x = bx, y = by, originY = by))
                }
            }

            // Update and draw each bullet, limit range to 80% of screen height
            val bulletSpeed = if (frozen) 0f else BULLET_SPEED
            var i = 0
            while (i < bullets.size) {
                val bullet = bullets[i]
                bullet.y -= bulletSpeed * speed
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
