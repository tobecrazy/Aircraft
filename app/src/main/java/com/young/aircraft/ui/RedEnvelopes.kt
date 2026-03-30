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

    // Envelope bitmaps: closed while active, open briefly after detonation.
    private val closedBitmap: Bitmap?
    private val openBitmap: Bitmap?
    private val rocketBitmap: Bitmap?

    private val envelopeSizePx: Int = ScreenUtils.dpToPx(context, 150.0f)
    private val rocketSizePx: Int = ScreenUtils.dpToPx(context, 50.0f)
    private val envelopeSpawnMarginPx: Float = ScreenUtils.dpToPx(context, 40.0f).toFloat()

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
        const val MAX_ON_SCREEN = 1
        const val DRIFT_SPEED = 2f
        const val ROCKET_SPEED = 20f
        const val HIT_FLASH_DURATION_MS = 100L
    }

    init {
        closedBitmap = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.red_box_1),
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
        val maxLeft = (screenWidth - envelopeSpawnMarginPx - envelopeSizePx.toFloat())
            .coerceAtLeast(envelopeSpawnMarginPx)
        if (maxLeft <= envelopeSpawnMarginPx) return envelopeSpawnMarginPx
        return envelopeSpawnMarginPx + Random.Default.nextFloat() * (maxLeft - envelopeSpawnMarginPx)
    }

    private fun getRandomTop(): Float {
        val minTop = envelopeSpawnMarginPx
        val maxTop = (screenHeight * 0.5f - envelopeSizePx.toFloat() - envelopeSpawnMarginPx)
            .coerceAtLeast(minTop)
        if (maxTop <= minTop) return minTop
        return minTop + Random.Default.nextFloat() * (maxTop - minTop)
    }

    private fun hasEnvelopeOnScreen(nowMs: Long = System.currentTimeMillis()): Boolean {
        return activeEnvelopes.count { !it.isExpired(nowMs) } >= MAX_ON_SCREEN
    }

    private fun spawnEnvelope() {
        if (hasEnvelopeOnScreen()) return
        val x = getRandomLeft()
        val y = getRandomTop()
        activeEnvelopes.add(RedEnvelopeState(x = x, y = y))
    }

    private fun updateSpawnTimer() {
        if (hasEnvelopeOnScreen()) {
            framesSinceLastSpawn = 0
            return
        }
        framesSinceLastSpawn = minOf(framesSinceLastSpawn + 1, SPAWN_INTERVAL_FRAMES)
        if (framesSinceLastSpawn < SPAWN_INTERVAL_FRAMES) return

        framesSinceLastSpawn = 0
        spawnEnvelope()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        drawEnvelopes(canvas)
        updateSpawnTimer()
        drawRockets(canvas)
        drawExplosions(canvas)
    }

    private fun drawEnvelopes(canvas: Canvas) {
        val iter = activeEnvelopes.iterator()
        while (iter.hasNext()) {
            val envelope = iter.next()
            val now = System.currentTimeMillis()
            if (envelope.isExpired(now)) {
                iter.remove()
                continue
            }

            if (!envelope.isDetonated()) {
                // Move downward
                envelope.y += DRIFT_SPEED * speed
                // Remove if off-screen bottom
                if (envelope.y > screenHeight) {
                    iter.remove()
                    continue
                }
                // Hit flash effect
                val paint = if (now - envelope.lastHitTime < HIT_FLASH_DURATION_MS) hitFlashPaint else mPaint
                closedBitmap?.let { canvas.drawBitmap(it, envelope.x, envelope.y, paint) }
            } else if (envelope.shouldShowOpenState(now)) {
                // Show open box briefly after detonation
                openBitmap?.let { canvas.drawBitmap(it, envelope.x, envelope.y, mPaint) }
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
        return envelope.registerHit()
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
