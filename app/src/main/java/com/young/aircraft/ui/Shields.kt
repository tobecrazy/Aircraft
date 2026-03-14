package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.ShieldState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

class Shields(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeShields = mutableListOf<ShieldState>()
    private var frameCounter: Int = 0
    private var spawnAttempted: Boolean = false
    var level: Int = 1

    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()
    private val screenHeight: Float = ScreenUtils.getScreenHeight(context).toFloat()
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi

    private val shieldBitmaps = arrayOfNulls<Bitmap>(3)
    private val shieldSizePx: Int = ScreenUtils.dpToPx(context, 100.0f)

    private val blinkPaint = Paint()

    companion object {
        const val LIFETIME_FRAMES = 450       // 15s at 30 FPS
        const val BLINK_THRESHOLD = 300       // blinking starts at frame 300 (last 5s)
        const val BLINK_TOGGLE_FRAMES = 6     // toggle every 6 frames
        const val SPAWN_DELAY_FRAMES = 300    // spawn after 10s into the level
    }

    init {
        shieldBitmaps[0] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.shield_1),
            shieldSizePx, shieldSizePx
        )?.also { it.density = screenDensity }

        shieldBitmaps[1] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.shield_2),
            shieldSizePx, shieldSizePx
        )?.also { it.density = screenDensity }

        shieldBitmaps[2] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.shield_3),
            shieldSizePx, shieldSizePx
        )?.also { it.density = screenDensity }
    }

    /**
     * Probability of a shield spawning this level.
     * Level 1: 90%, Level 5: 50%, Level 10: 5%
     */
    private fun getSpawnProbability(): Float {
        return maxOf(0.05f, 1.0f - (level - 1) * 0.1f)
    }

    private fun trySpawnShield() {
        if (spawnAttempted) return
        spawnAttempted = true

        val rng = Random(System.nanoTime())
        if (rng.nextFloat() > getSpawnProbability()) return

        val marginPx = ScreenUtils.dpToPx(context, 40.0f)
        val x = marginPx + rng.nextFloat() * (screenWidth - 2 * marginPx - shieldSizePx)
        val y = screenHeight * 0.15f + rng.nextFloat() * (screenHeight * 0.50f)
        activeShields.add(
            ShieldState(
                x = x,
                y = y,
                spawnFrame = frameCounter,
                bitmapIndex = rng.nextInt(3)
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        frameCounter++

        if (frameCounter >= SPAWN_DELAY_FRAMES) {
            trySpawnShield()
        }

        val iter = activeShields.iterator()
        while (iter.hasNext()) {
            val shield = iter.next()
            val age = frameCounter - shield.spawnFrame

            if (age >= LIFETIME_FRAMES || shield.collected) {
                iter.remove()
                continue
            }

            // Blinking in the last 5 seconds
            val alpha = if (age >= BLINK_THRESHOLD) {
                val blinkCycle = (age - BLINK_THRESHOLD) / BLINK_TOGGLE_FRAMES
                if (blinkCycle % 2 == 0) 255 else 80
            } else {
                255
            }
            blinkPaint.alpha = alpha

            val bmp = shieldBitmaps[shield.bitmapIndex]
            bmp?.let { canvas.drawBitmap(it, shield.x, shield.y, blinkPaint) }
        }
    }

    fun getShieldBounds(shield: ShieldState): RectF {
        return RectF(
            shield.x, shield.y,
            shield.x + shieldSizePx, shield.y + shieldSizePx
        )
    }

    fun clearAll() {
        activeShields.clear()
        frameCounter = 0
        spawnAttempted = false
    }

    override fun updateGame() {}

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
