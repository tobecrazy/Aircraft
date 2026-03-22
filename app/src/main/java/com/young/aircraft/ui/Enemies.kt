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
import com.young.aircraft.data.EnemyBullet
import com.young.aircraft.data.EnemyState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

/**
 * Create by Young
 **/
class Enemies(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeEnemies = mutableListOf<EnemyState>()
    private val activeExplosions = mutableListOf<ExplosionEffect>()
    private var framesSinceLastSpawn: Int = 0
    private val bitmapList = mutableListOf<Bitmap?>()
    private var bulletBitmap: Bitmap? = null
    var level: Int = 1
    var spawnPaused: Boolean = false
    var frozen: Boolean = false  // When true, enemies and their bullets don't move

    // Cached resized enemy bitmaps (avoid per-frame allocation)
    private val cachedEnemyBitmaps = mutableListOf<Bitmap?>()

    // Paint with red tint for enemy bullets
    private val bulletPaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1.5f, 0.5f, 0.5f, 0f, 30f,   // R
                    0f, 0.2f, 0f, 0f, 0f,     // G
                    0f, 0f, 0.2f, 0f, 0f,     // B
                    0f, 0f, 0f, 1f, 0f      // A
                )
            )
        )
    }

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

    // Bullet range: 60% of screen height
    private val maxBulletRange: Float = ScreenUtils.getScreenHeight(context).toFloat() * 0.6f
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi

    companion object {
        const val BASE_ENEMY_MOVE_SPEED = 3f
        const val BASE_ENEMY_BULLET_SPEED = 6f
        const val LEVEL_SPEED_INCREMENT = 1.5f
        const val BASE_ENEMIES_PER_ROW = 5

        // Base bullet spacing (high = slow fire rate), decreases with level
        const val BASE_BULLET_SPACING_DP = 350f
        const val MIN_BULLET_SPACING_DP = 250f
        const val SPACING_DECREASE_PER_LEVEL = 15f
    }

    fun getEnemyMoveSpeed(): Float = BASE_ENEMY_MOVE_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT

    fun getEnemyBulletSpeed(): Float {
        val moveSpeed = getEnemyMoveSpeed()
        return moveSpeed + BASE_ENEMY_BULLET_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT
    }

    fun getEnemiesPerRow(): Int = BASE_ENEMIES_PER_ROW + level

    fun getSpawnIntervalFrames(): Int = 90 - (level - 1) * 5

    /** Bullet spacing decreases with level → more bullets at higher levels */
    fun getBulletSpacingDp(): Float {
        val spacing = BASE_BULLET_SPACING_DP - (level - 1) * SPACING_DECREASE_PER_LEVEL
        return spacing.coerceAtLeast(MIN_BULLET_SPACING_DP)
    }

    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()

    init {
        val enemyResIds = intArrayOf(
            R.drawable.enemy_1, R.drawable.enemy_2, R.drawable.enemy_3,
            R.drawable.enemy_4, R.drawable.enemy_5, R.drawable.enemy_6,
            R.drawable.enemy_7, R.drawable.enemy_8, R.drawable.enemy_9,
            R.drawable.enemy_10, R.drawable.enemy_11, R.drawable.enemy_12,
            R.drawable.enemy_13, R.drawable.enemy_14, R.drawable.enemy_15
        )
        for (resId in enemyResIds) {
            bitmapList.add(BitmapUtils.readBitMap(context, resId))
        }

        // Pre-cache resized enemy bitmaps
        val enemySizePx = ScreenUtils.dpToPx(context, 48.0f)
        for (bmp in bitmapList) {
            cachedEnemyBitmaps.add(
                BitmapUtils.resizeBitmap(bmp, enemySizePx, enemySizePx, 180.0f)
            )
        }

        // Use the player's bullet_up bitmap, rotated 180° so it points down, at same 40dp size
        val originBullet = BitmapUtils.readBitMap(context, R.drawable.bullet_up)
        bulletBitmap = BitmapUtils.resizeBitmap(
            originBullet,
            ScreenUtils.dpToPx(context, 40.0f),
            ScreenUtils.dpToPx(context, 40.0f),
            180.0f
        )
        // Match player bullet density so canvas renders both at the same visual size
        bulletBitmap?.density = screenDensity
    }

    private fun getRandomEnemyBitmapIndex(): Int {
        val random = Random(System.nanoTime())
        val count = bitmapList.size
        var index: Int = random.nextInt() % count
        while (index < 0) {
            index = random.nextInt() % count
        }
        return index
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

    private fun spawnRow() {
        val count = getEnemiesPerRow()
        val enemySizeDp = 48.0f
        val enemySizePx = ScreenUtils.dpToPx(context, enemySizeDp).toFloat()
        // Randomize each enemy's starting Y within a spread range above screen
        val random = Random(System.nanoTime())
        val baseY = -enemySizePx
        val spreadPx = ScreenUtils.dpToPx(context, 80.0f).toFloat()
        for (i in 0 until count) {
            val x = getRandomLeft()
            val bmpIndex = getRandomEnemyBitmapIndex()
            // Each enemy gets a random Y offset from baseY to (baseY - spreadPx)
            val randomYOffset = random.nextFloat() * spreadPx
            activeEnemies.add(
                EnemyState(
                    x = x,
                    y = baseY - randomYOffset,
                    bitmap = bitmapList[bmpIndex],
                    health = 1f
                )
            )
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val screenHeight = ScreenUtils.getScreenHeight(context).toFloat()
        val moveSpeed = if (frozen) 0f else getEnemyMoveSpeed()

        // Spawn timer (only when not frozen)
        if (!frozen) {
            framesSinceLastSpawn++
            if (!spawnPaused && framesSinceLastSpawn >= getSpawnIntervalFrames()) {
                framesSinceLastSpawn = 0
                spawnRow()
            }
        }

        // Move and draw alive enemies
        val iter = activeEnemies.iterator()
        while (iter.hasNext()) {
            val enemy = iter.next()
            if (!enemy.isDestroyed()) {
                enemy.y += moveSpeed * speed
                // Remove if off-screen bottom
                if (enemy.y > screenHeight) {
                    iter.remove()
                    continue
                }
                // Draw using cached bitmap
                val bmpIndex = bitmapList.indexOf(enemy.bitmap)
                val cachedBmp = if (bmpIndex >= 0) cachedEnemyBitmaps[bmpIndex] else null
                cachedBmp?.let { canvas.drawBitmap(it, enemy.x, enemy.y, mPaint) }
            } else if (enemy.isExpired()) {
                iter.remove()
                continue
            }
        }

        drawEnemyBullets(canvas)
        drawDestroyedEnemies(canvas)
    }

    private fun drawEnemyBullets(canvas: Canvas) {
        val screenHeight = ScreenUtils.getScreenHeight(context).toFloat()
        val currentBulletSpeed = if (frozen) 0f else getEnemyBulletSpeed()
        val spacingDp = getBulletSpacingDp()
        val spacingPx = ScreenUtils.dpToPx(context, spacingDp)
        val enemySizePx = ScreenUtils.dpToPx(context, 48.0f)
        val random = Random(System.nanoTime())
        // Small random jitter on spacing (±15%) so bullets don't fire in lock-step
        val jitterRange = (spacingPx * 0.15f).toInt().coerceAtLeast(1)

        bulletBitmap?.let { bmp ->
            // Compute density-scaled rendered size for positioning
            val scale = if (canvas.density > 0) canvas.density.toFloat() / screenDensity else 1f
            val renderedW = bmp.width * scale

            for (enemy in activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyCenterX = enemy.x + enemySizePx / 2f
                val bulletSpawnX = enemyCenterX - renderedW / 2f

                // Fire bullet when spacing threshold reached (with jitter) - only when not frozen
                if (!frozen) {
                    val jitter = random.nextInt(jitterRange * 2 + 1) - jitterRange
                    val effectiveSpacing = spacingPx + jitter
                    if (enemy.bullets.isEmpty() || (enemy.bullets.last().y - enemy.y) > effectiveSpacing) {
                        val spawnY = enemy.y + enemySizePx
                        enemy.bullets.add(EnemyBullet(y = spawnY, originY = spawnY))
                    }
                }

                val bulletIter = enemy.bullets.iterator()
                while (bulletIter.hasNext()) {
                    val bullet = bulletIter.next()
                    bullet.y += currentBulletSpeed * speed
                    val distanceTraveled = bullet.y - bullet.originY
                    // Remove if off-screen or exceeded 60% range
                    if (bullet.y > screenHeight || distanceTraveled > maxBulletRange) {
                        bulletIter.remove()
                    } else {
                        canvas.drawBitmap(bmp, bulletSpawnX, bullet.y, bulletPaint)
                    }
                }
            }
        }
    }

    private fun drawDestroyedEnemies(canvas: Canvas) {
        // Draw hit flash: show enemy with white tint for first 100ms after destruction
        for (enemy in activeEnemies) {
            if (enemy.isDestroyed() && !enemy.isExpired()) {
                val elapsed = System.currentTimeMillis() - enemy.destroyedTime
                if (elapsed <= 100L) {
                    val bmpIndex = bitmapList.indexOf(enemy.bitmap)
                    val cachedBmp = if (bmpIndex >= 0) cachedEnemyBitmaps[bmpIndex] else null
                    cachedBmp?.let { canvas.drawBitmap(it, enemy.x, enemy.y, hitFlashPaint) }
                }
            }
        }

        // Draw and prune explosion effects
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

    fun getEnemyBullets(): List<Triple<Float, Float, EnemyBullet>> {
        val result = mutableListOf<Triple<Float, Float, EnemyBullet>>()
        bulletBitmap?.let { bmp ->
            val enemySizePx = ScreenUtils.dpToPx(context, 48.0f)
            // Use raw bitmap width (density scaling is handled by getBulletBounds)
            val halfW = bmp.width / 2f
            for (enemy in activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyCenterX = enemy.x + enemySizePx / 2f
                val bulletX = enemyCenterX - halfW
                for (bullet in enemy.bullets) {
                    result.add(Triple(bulletX, bullet.y, bullet))
                }
            }
        }
        return result
    }

    fun getBulletBounds(x: Float, y: Float): RectF {
        bulletBitmap?.let {
            // Use raw bitmap pixel size for bounds (same coordinate space as drawing)
            return RectF(x, y, x + it.width, y + it.height)
        }
        return RectF()
    }

    fun hitEnemy(enemy: EnemyState): Boolean {
        enemy.health = -1f
        enemy.destroyedTime = System.currentTimeMillis()
        // Spawn explosion at enemy center
        val enemySizePx = ScreenUtils.dpToPx(context, 48.0f).toFloat()
        activeExplosions.add(
            ExplosionEffect(
                centerX = enemy.x + enemySizePx / 2f,
                centerY = enemy.y + enemySizePx / 2f,
                size = enemySizePx
            )
        )
        return true
    }

    fun clearExplosions() {
        activeExplosions.clear()
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
}
