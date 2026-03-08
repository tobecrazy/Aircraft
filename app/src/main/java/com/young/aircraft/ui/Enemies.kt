package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.EnemyState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

/**
 * Create by Young
 **/
class Enemies(var context: Context, var speed: Float) : DrawBaseObject(context) {
    val activeEnemies = mutableListOf<EnemyState>()
    private var framesSinceLastSpawn: Int = 0
    private val bitmapList = mutableListOf<Bitmap?>()
    private var bulletBitmap: Bitmap? = null
    var level: Int = 1

    // Cached resized enemy bitmaps (avoid per-frame allocation)
    private val cachedEnemyBitmaps = mutableListOf<Bitmap?>()

    companion object {
        const val BASE_ENEMY_MOVE_SPEED = 3f
        const val BASE_ENEMY_BULLET_SPEED = 6f
        const val LEVEL_SPEED_INCREMENT = 1.5f
        const val BASE_ENEMY_HEALTH = 100f
        const val HEALTH_PER_LEVEL = 20f
        const val BASE_ENEMIES_PER_ROW = 5
    }

    fun getEnemyMoveSpeed(): Float = BASE_ENEMY_MOVE_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT

    fun getEnemyBulletSpeed(): Float {
        val moveSpeed = getEnemyMoveSpeed()
        return moveSpeed + BASE_ENEMY_BULLET_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT
    }

    fun getEnemyHealth(): Float = BASE_ENEMY_HEALTH + (level - 1) * HEALTH_PER_LEVEL

    fun getEnemiesPerRow(): Int = BASE_ENEMIES_PER_ROW + level

    fun getSpawnIntervalFrames(): Int = 90 - (level - 1) * 5

    private val enemyX: Float = ScreenUtils.getScreenWidth(context).toFloat()

    init {
        val originBitmap1 = BitmapUtils.readBitMap(context, R.drawable.enemy_1)
        val originBitmap2 = BitmapUtils.readBitMap(context, R.drawable.enemy_2)
        val originBitmap3 = BitmapUtils.readBitMap(context, R.drawable.enemy_3)
        val originBitmap4 = BitmapUtils.readBitMap(context, R.drawable.enemy_4)
        bitmapList.add(originBitmap1)
        bitmapList.add(originBitmap2)
        bitmapList.add(originBitmap3)
        bitmapList.add(originBitmap4)

        // Pre-cache resized enemy bitmaps
        val enemySizePx = ScreenUtils.dpToPx(context, 48.0f)
        for (bmp in bitmapList) {
            cachedEnemyBitmaps.add(
                BitmapUtils.resizeBitmap(bmp, enemySizePx, enemySizePx, 180.0f)
            )
        }

        val originBullet = BitmapUtils.readBitMap(context, R.drawable.bullet_down)
        bulletBitmap = BitmapUtils.resizeBitmap(
            originBullet,
            ScreenUtils.dpToPx(context, 25.0f),
            ScreenUtils.dpToPx(context, 25.0f)
        )
    }

    private fun getRandomEnemyBitmapIndex(): Int {
        val random = Random(System.nanoTime())
        var index: Int = random.nextInt() % 4
        while (index < 0) {
            index = random.nextInt() % 4
        }
        return index
    }

    private fun getRandomLeft(): Float {
        val random = Random(System.nanoTime())
        val start = ScreenUtils.dpToPx(context, 40.0f)
        val end = ScreenUtils.getScreenWidth(context) - ScreenUtils.dpToPx(context, 40.0f)
        var randomX = enemyX * random.nextFloat()
        while (randomX <= start || randomX >= end) {
            randomX = enemyX * random.nextFloat()
        }
        return randomX
    }

    private fun spawnRow() {
        val count = getEnemiesPerRow()
        val health = getEnemyHealth()
        val startY = -ScreenUtils.dpToPx(context, 48.0f).toFloat()
        for (i in 0 until count) {
            val x = getRandomLeft()
            val bmpIndex = getRandomEnemyBitmapIndex()
            activeEnemies.add(
                EnemyState(
                    x = x,
                    y = startY,
                    bitmap = bitmapList[bmpIndex],
                    health = health
                )
            )
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val screenHeight = ScreenUtils.getScreenHeight(context).toFloat()
        val moveSpeed = getEnemyMoveSpeed()

        // Spawn timer
        framesSinceLastSpawn++
        if (framesSinceLastSpawn >= getSpawnIntervalFrames()) {
            framesSinceLastSpawn = 0
            spawnRow()
        }

        // Move and draw alive enemies
        val enemySizePx = ScreenUtils.dpToPx(context, 48.0f)
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
        val currentBulletSpeed = getEnemyBulletSpeed()
        bulletBitmap?.let { bmp ->
            for (enemy in activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyCenterX = enemy.x + ScreenUtils.dpToPx(context, 24.0f)
                if (enemy.bullets.isEmpty() || (enemy.bullets.last() - enemy.y) > ScreenUtils.dpToPx(context, 150.0f)) {
                    enemy.bullets.add(enemy.y + ScreenUtils.dpToPx(context, 48.0f))
                }
                val bulletIter = enemy.bullets.iterator()
                val updatedBullets = mutableListOf<Float>()
                while (bulletIter.hasNext()) {
                    val bulletY = bulletIter.next()
                    val newY = bulletY + currentBulletSpeed * speed
                    if (newY > screenHeight) {
                        bulletIter.remove()
                    } else {
                        updatedBullets.add(newY)
                        canvas.drawBitmap(bmp, enemyCenterX - bmp.width / 2, newY, mPaint)
                    }
                }
                // Update bullet positions
                enemy.bullets.clear()
                enemy.bullets.addAll(updatedBullets)
            }
        }
    }

    private fun drawDestroyedEnemies(canvas: Canvas) {
        val destroyPaint = Paint().apply {
            color = Color.RED
            alpha = 128
            style = Paint.Style.FILL
        }
        for (enemy in activeEnemies) {
            if (enemy.isDestroyed() && !enemy.isExpired()) {
                val size = ScreenUtils.dpToPx(context, 48.0f).toFloat()
                canvas.drawCircle(
                    enemy.x + size / 2,
                    enemy.y + size / 2,
                    size / 2,
                    destroyPaint
                )
            }
        }
    }

    fun getEnemyBullets(): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()
        bulletBitmap?.let { bmp ->
            for (enemy in activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyCenterX = enemy.x + ScreenUtils.dpToPx(context, 24.0f)
                for (bulletY in enemy.bullets) {
                    result.add(Pair(enemyCenterX - bmp.width / 2, bulletY))
                }
            }
        }
        return result
    }

    fun getBulletBounds(x: Float, y: Float): RectF {
        bulletBitmap?.let {
            return RectF(x, y, x + it.width, y + it.height)
        }
        return RectF()
    }

    fun hitEnemy(enemy: EnemyState): Boolean {
        enemy.health -= 20f
        if (enemy.isDestroyed()) {
            enemy.destroyedTime = System.currentTimeMillis()
            return true
        }
        return false
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
