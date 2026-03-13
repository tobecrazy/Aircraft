package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.MedicalKitState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

class MedicalKits(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeKits = mutableListOf<MedicalKitState>()
    private var framesSinceLastSpawn: Int = 0
    private var totalSpawnedThisLevel: Int = 0
    private var frameCounter: Int = 0
    var level: Int = 1

    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()
    private val screenHeight: Float = ScreenUtils.getScreenHeight(context).toFloat()
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi

    private val kitBitmaps = arrayOfNulls<Bitmap>(2)
    private val kitSizePx: Int = ScreenUtils.dpToPx(context, 120.0f)

    private val blinkPaint = Paint()

    companion object {
        const val LIFETIME_FRAMES = 450       // 15s at 30 FPS
        const val BLINK_THRESHOLD = 300       // blinking starts at frame 300 (last 5s)
        const val BLINK_TOGGLE_FRAMES = 6     // toggle every 6 frames (~2.5Hz)
    }

    init {
        kitBitmaps[0] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.medical_kit_1),
            kitSizePx, kitSizePx
        )?.also { it.density = screenDensity }

        kitBitmaps[1] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.medical_kit_2),
            kitSizePx, kitSizePx
        )?.also { it.density = screenDensity }
    }

    private fun getSpawnIntervalFrames(): Int = 450 + 150 * (level - 1)

    private fun getMaxKitsPerLevel(): Int = maxOf(0, 2 - (level - 1) / 4)

    private fun spawnKit() {
        if (totalSpawnedThisLevel >= getMaxKitsPerLevel()) return
        // Max 1 uncollected kit on screen at a time
        if (activeKits.any { !it.collected }) return

        val marginPx = ScreenUtils.dpToPx(context, 40.0f)
        val rng = Random(System.nanoTime())
        val x = marginPx + rng.nextFloat() * (screenWidth - 2 * marginPx - kitSizePx)
        val y = screenHeight * 0.15f + rng.nextFloat() * (screenHeight * 0.55f)
        activeKits.add(
            MedicalKitState(
                x = x,
                y = y,
                spawnFrame = frameCounter,
                bitmapIndex = rng.nextInt(2)
            )
        )
        totalSpawnedThisLevel++
    }

    override fun onDraw(canvas: Canvas) {
        frameCounter++
        framesSinceLastSpawn++

        if (framesSinceLastSpawn >= getSpawnIntervalFrames()) {
            framesSinceLastSpawn = 0
            spawnKit()
        }

        val iter = activeKits.iterator()
        while (iter.hasNext()) {
            val kit = iter.next()
            val age = frameCounter - kit.spawnFrame

            // Remove expired or collected kits
            if (age >= LIFETIME_FRAMES || kit.collected) {
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

            val bmp = kitBitmaps[kit.bitmapIndex]
            bmp?.let { canvas.drawBitmap(it, kit.x, kit.y, blinkPaint) }
        }
    }

    fun getKitBounds(kit: MedicalKitState): RectF {
        return RectF(
            kit.x, kit.y,
            kit.x + kitSizePx, kit.y + kitSizePx
        )
    }

    fun clearAll() {
        activeKits.clear()
        framesSinceLastSpawn = 0
        totalSpawnedThisLevel = 0
        frameCounter = 0
    }

    override fun updateGame() {}

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
