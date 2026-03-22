package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.TimeFreezeState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

/**
 * Time Freeze power-up:
 * - Spawns randomly on screen (max 3 per level, lower probability at higher levels)
 * - Player touching it freezes enemies and their bullets for 5 seconds
 * - Enemy touching it freezes player and their bullets for 5 seconds
 * - Timer disappears after 5 seconds if not collected (with blinking effect)
 */
class TimeFreezes(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeTimeFreezes = mutableListOf<TimeFreezeState>()
    private var frameCounter: Int = 0
    private var spawnCountThisLevel: Int = 0
    private var nextSpawnFrame: Int = 0
    var level: Int = 1

    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()
    private val screenHeight: Float = ScreenUtils.getScreenHeight(context).toFloat()
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi

    private val timeFreezeBitmaps = arrayOfNulls<Bitmap>(3)
    private val timeFreezeSizePx: Int = ScreenUtils.dpToPx(context, 80.0f)

    private val blinkPaint = Paint()

    // Freeze state tracking
    private var playerFreezeEndFrame: Int = 0
    private var enemyFreezeEndFrame: Int = 0

    companion object {
        const val LIFETIME_FRAMES = 150       // 5s at 30 FPS (display time before auto-disappear)
        const val BLINK_THRESHOLD = 90        // blinking starts at frame 90 (last 2s)
        const val BLINK_TOGGLE_FRAMES = 4     // toggle every 4 frames for faster blink
        const val SPAWN_DELAY_MIN_FRAMES = 300    // min 10s between spawns
        const val SPAWN_DELAY_MAX_FRAMES = 600    // max 20s between spawns
        const val MAX_SPAWNS_PER_LEVEL = 3    // max 3 time freezes per level
        const val FREEZE_DURATION_FRAMES = 150   // 5s freeze duration at 30 FPS
    }

    init {
        timeFreezeBitmaps[0] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.timer_1),
            timeFreezeSizePx, timeFreezeSizePx
        )?.also { it.density = screenDensity }

        timeFreezeBitmaps[1] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.timer_2),
            timeFreezeSizePx, timeFreezeSizePx
        )?.also { it.density = screenDensity }

        timeFreezeBitmaps[2] = BitmapUtils.resizeBitmap(
            BitmapUtils.readBitMap(context, R.drawable.timer_3),
            timeFreezeSizePx, timeFreezeSizePx
        )?.also { it.density = screenDensity }

        // Initialize first spawn time
        scheduleNextSpawn()
    }

    /**
     * Probability of a time freeze spawning this level.
     * Level 1: 80%, Level 5: 50%, Level 10: 20%
     * Higher levels = lower probability
     */
    private fun getSpawnProbability(): Float {
        return maxOf(0.2f, 0.80f - (level - 1) * 0.067f)
    }

    private fun scheduleNextSpawn() {
        val rng = Random(System.nanoTime())
        val delay = SPAWN_DELAY_MIN_FRAMES + rng.nextInt(SPAWN_DELAY_MAX_FRAMES - SPAWN_DELAY_MIN_FRAMES)
        nextSpawnFrame = frameCounter + delay
    }

    private fun trySpawnTimeFreeze() {
        if (spawnCountThisLevel >= MAX_SPAWNS_PER_LEVEL) return
        if (frameCounter < nextSpawnFrame) return

        val rng = Random(System.nanoTime())
        // Check spawn probability
        if (rng.nextFloat() > getSpawnProbability()) {
            // Failed probability check, schedule next attempt
            scheduleNextSpawn()
            return
        }

        val marginPx = ScreenUtils.dpToPx(context, 50.0f)
        val x = marginPx + rng.nextFloat() * (screenWidth - 2 * marginPx - timeFreezeSizePx)
        val y = screenHeight * 0.20f + rng.nextFloat() * (screenHeight * 0.45f)

        activeTimeFreezes.add(
            TimeFreezeState(
                x = x,
                y = y,
                spawnFrame = frameCounter,
                bitmapIndex = rng.nextInt(3)
            )
        )
        spawnCountThisLevel++
        scheduleNextSpawn()
    }

    override fun onDraw(canvas: Canvas) {
        frameCounter++

        // Try to spawn new time freeze
        trySpawnTimeFreeze()

        val iter = activeTimeFreezes.iterator()
        while (iter.hasNext()) {
            val timeFreeze = iter.next()
            val age = frameCounter - timeFreeze.spawnFrame

            // Remove if collected or expired
            if (timeFreeze.collected || age >= LIFETIME_FRAMES) {
                iter.remove()
                continue
            }

            // Blinking in the last 2 seconds
            val alpha = if (age >= BLINK_THRESHOLD) {
                val blinkCycle = (age - BLINK_THRESHOLD) / BLINK_TOGGLE_FRAMES
                if (blinkCycle % 2 == 0) 255 else 40
            } else {
                255
            }
            blinkPaint.alpha = alpha

            val bmp = timeFreezeBitmaps[timeFreeze.bitmapIndex]
            bmp?.let { canvas.drawBitmap(it, timeFreeze.x, timeFreeze.y, blinkPaint) }
        }
    }

    fun getTimeFreezeBounds(timeFreeze: TimeFreezeState): RectF {
        return RectF(
            timeFreeze.x, timeFreeze.y,
            timeFreeze.x + timeFreezeSizePx, timeFreeze.y + timeFreezeSizePx
        )
    }

    /**
     * Called when player collects the time freeze.
     * Enemies and their bullets will be frozen.
     */
    fun collectByPlayer(timeFreeze: TimeFreezeState) {
        timeFreeze.collected = true
        timeFreeze.collectedByPlayer = true
        enemyFreezeEndFrame = frameCounter + FREEZE_DURATION_FRAMES
    }

    /**
     * Called when an enemy collects the time freeze.
     * Player and their bullets will be frozen.
     */
    fun collectByEnemy(timeFreeze: TimeFreezeState) {
        timeFreeze.collected = true
        timeFreeze.collectedByPlayer = false
        playerFreezeEndFrame = frameCounter + FREEZE_DURATION_FRAMES
    }

    /**
     * Returns true if enemies should be frozen (player collected a time freeze)
     */
    fun isEnemyFrozen(): Boolean {
        return frameCounter < enemyFreezeEndFrame
    }

    /**
     * Returns true if player should be frozen (enemy collected a time freeze)
     */
    fun isPlayerFrozen(): Boolean {
        return frameCounter < playerFreezeEndFrame
    }

    /**
     * Get remaining freeze frames for enemies
     */
    fun getEnemyFreezeRemainingFrames(): Int {
        return (enemyFreezeEndFrame - frameCounter).coerceAtLeast(0)
    }

    /**
     * Get remaining freeze frames for player
     */
    fun getPlayerFreezeRemainingFrames(): Int {
        return (playerFreezeEndFrame - frameCounter).coerceAtLeast(0)
    }

    fun clearAll() {
        activeTimeFreezes.clear()
        frameCounter = 0
        spawnCountThisLevel = 0
        playerFreezeEndFrame = 0
        enemyFreezeEndFrame = 0
        scheduleNextSpawn()
    }

    override fun updateGame() {}

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
