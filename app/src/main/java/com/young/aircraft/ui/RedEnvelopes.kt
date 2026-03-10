package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.RedEnvelopeState
import com.young.aircraft.data.RocketState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

class RedEnvelopes(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeEnvelopes = mutableListOf<RedEnvelopeState>()
    val activeRockets = mutableListOf<RocketState>()
    private val activeExplosions = mutableListOf<ExplosionEffect>()
    private var framesSinceLastSpawn: Int = 0
    var level: Int = 1

    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()
    private val screenHeight: Float = ScreenUtils.getScreenHeight(context).toFloat()
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi

    // Envelope bitmaps: 3 HP = closed, 2-1 HP = ribbon, 0 HP = open
    private val closedBitmap: Bitmap?
    private val ribbonBitmap: Bitmap?
    private val openBitmap: Bitmap?
    private val rocketBitmap: Bitmap?

    private val envelopeSizePx: Int = ScreenUtils.dpToPx(context, 72.0f)
    private val rocketSizePx: Int = ScreenUtils.dpToPx(context, 50.0f)

    // Paint with white tint for hit flash effect
    private val hitFlashPaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    companion object {
        const val SPAWN_INTERVAL_FRAMES = 300
        const val MAX_ON_SCREEN = 2
        const val DRIFT_SPEED = 2f
        const val ROCKET_SPEED = 20f
        const val HIT_FLASH_DURATION_MS = 100L
    }

    init {
        closedBitmap = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.red_box_1),
            envelopeSizePx, envelopeSizePx
        )?.also { it.density = screenDensity }

        ribbonBitmap = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.red_box_3),
            envelopeSizePx, envelopeSizePx
        )?.also { it.density = screenDensity }

        openBitmap = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.red_box_2),
            envelopeSizePx, envelopeSizePx
        )?.also { it.density = screenDensity }

        rocketBitmap = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.rocket),
            rocketSizePx, rocketSizePx
        )?.also { it.density = screenDensity }
    }

    private fun getRandomLeft(): Float {
        val random = Random(System.nanoTime())
        val start = ScreenUtils.dpToPx(context, 40.0f)
        val end = ScreenUtils.getScreenWidth(context) - ScreenUtils.dpToPx(context, 40.0f)
        var randomX = screenWidth * random.nextFloat()
        while (randomX <= start || randomX >= end) {
            randomX = screenWidth * random.nextFloat()
        }
        return randomX
    }

    private fun spawnEnvelope() {
        if (activeEnvelopes.count { !it.isDetonated() } >= MAX_ON_SCREEN) return
        val x = getRandomLeft()
        val y = -envelopeSizePx.toFloat()
        activeEnvelopes.add(RedEnvelopeState(x = x, y = y))
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // Spawn timer
        framesSinceLastSpawn++
        if (framesSinceLastSpawn >= SPAWN_INTERVAL_FRAMES) {
            framesSinceLastSpawn = 0
            spawnEnvelope()
        }

        drawEnvelopes(canvas)
        drawRockets(canvas)
        drawExplosions(canvas)
    }

    private fun drawEnvelopes(canvas: Canvas) {
        val iter = activeEnvelopes.iterator()
        while (iter.hasNext()) {
            val envelope = iter.next()
            if (!envelope.isDetonated()) {
                // Move downward
                envelope.y += DRIFT_SPEED * speed
                // Remove if off-screen bottom
                if (envelope.y > screenHeight) {
                    iter.remove()
                    continue
                }
                // Select bitmap by HP
                val bmp = when (envelope.hitPoints) {
                    3 -> closedBitmap
                    else -> ribbonBitmap
                }
                // Hit flash effect
                val now = System.currentTimeMillis()
                val paint = if (now - envelope.lastHitTime < HIT_FLASH_DURATION_MS) hitFlashPaint else mPaint
                bmp?.let { canvas.drawBitmap(it, envelope.x, envelope.y, paint) }
            } else if (envelope.isExpired()) {
                iter.remove()
            } else {
                // Show open box briefly after detonation
                val elapsed = System.currentTimeMillis() - envelope.destroyedTime
                if (elapsed <= 200L) {
                    openBitmap?.let { canvas.drawBitmap(it, envelope.x, envelope.y, mPaint) }
                }
            }
        }
    }

    private fun drawRockets(canvas: Canvas) {
        val rocketBmp = rocketBitmap ?: return
        val iter = activeRockets.iterator()
        while (iter.hasNext()) {
            val rocket = iter.next()
            if (!rocket.active) {
                iter.remove()
                continue
            }
            // Move upward
            rocket.y -= ROCKET_SPEED * speed
            // Remove if off-screen top
            if (rocket.y + rocketSizePx < 0) {
                iter.remove()
                continue
            }
            canvas.drawBitmap(rocketBmp, rocket.x, rocket.y, mPaint)
        }
    }

    private fun drawExplosions(canvas: Canvas) {
        val iter = activeExplosions.iterator()
        while (iter.hasNext()) {
            val explosion = iter.next()
            if (explosion.isFinished()) {
                iter.remove()
            } else {
                explosion.draw(canvas)
            }
        }
    }

    fun hitEnvelope(envelope: RedEnvelopeState): Boolean {
        envelope.hitPoints--
        envelope.lastHitTime = System.currentTimeMillis()
        if (envelope.isDetonated()) {
            envelope.destroyedTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun launchRocket(playerX: Float, playerY: Float, playerW: Float) {
        val rocketBmp = rocketBitmap ?: return
        val scale = if (screenDensity > 0) screenDensity.toFloat() / screenDensity else 1f
        val renderedW = rocketBmp.width * scale
        val rx = playerX + playerW / 2f - renderedW / 2f
        val ry = playerY
        activeRockets.add(RocketState(x = rx, y = ry))
    }

    fun triggerRocketExplosion(impactX: Float, impactY: Float) {
        val blastSize = minOf(screenWidth, screenHeight) * 0.20f
        activeExplosions.add(
            ExplosionEffect(
                centerX = impactX,
                centerY = impactY,
                size = blastSize,
                scale = 1.5f
            )
        )
    }

    fun getEnvelopeBounds(envelope: RedEnvelopeState): RectF {
        return RectF(
            envelope.x, envelope.y,
            envelope.x + envelopeSizePx, envelope.y + envelopeSizePx
        )
    }

    fun getRocketBounds(rocket: RocketState): RectF {
        return RectF(
            rocket.x, rocket.y,
            rocket.x + rocketSizePx, rocket.y + rocketSizePx
        )
    }

    fun clearAll() {
        activeEnvelopes.clear()
        activeRockets.clear()
        activeExplosions.clear()
        framesSinceLastSpawn = 0
    }

    override fun updateGame() {}

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
