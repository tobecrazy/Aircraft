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
    var enemyY: Float = 0F
    private var enemyX: Float = 0F
    val bitmapList = mutableListOf<Bitmap?>()
    val enemiesMap = mutableMapOf<Float, Bitmap?>()
    val enemyStates = mutableListOf<EnemyState>()
    private var bulletBitmap: Bitmap? = null
    var level: Int = 1

    companion object {
        const val BASE_ENEMY_MOVE_SPEED = 3f
        const val BASE_ENEMY_BULLET_SPEED = 6f
        const val LEVEL_SPEED_INCREMENT = 1.5f
    }

    fun getEnemyMoveSpeed(): Float = BASE_ENEMY_MOVE_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT

    fun getEnemyBulletSpeed(): Float {
        val moveSpeed = getEnemyMoveSpeed()
        return moveSpeed + BASE_ENEMY_BULLET_SPEED + (level - 1) * LEVEL_SPEED_INCREMENT
    }

    init {
        enemyY = getRandomTop()
        enemyX = ScreenUtils.getScreenWidth(context).toFloat()
        val originBitmap1 = BitmapUtils.readBitMap(context, R.drawable.enemy_1)
        val originBitmap2 = BitmapUtils.readBitMap(context, R.drawable.enemy_2)
        val originBitmap3 = BitmapUtils.readBitMap(context, R.drawable.enemy_3)
        val originBitmap4 = BitmapUtils.readBitMap(context, R.drawable.enemy_4)
        bitmapList.add(originBitmap1)
        bitmapList.add(originBitmap2)
        bitmapList.add(originBitmap3)
        bitmapList.add(originBitmap4)

        val originBullet = BitmapUtils.readBitMap(context, R.drawable.bullet_down)
        bulletBitmap = BitmapUtils.resizeBitmap(
            originBullet,
            ScreenUtils.dpToPx(context, 25.0f),
            ScreenUtils.dpToPx(context, 25.0f)
        )

        refreshData()
    }

    private fun getRandomEnemyBitmap(): Bitmap? {
        val random = Random(System.nanoTime())
        var index: Int = random.nextInt() % 4
        while (index < 0) {
            index = random.nextInt() % 4
        }
        return bitmapList[index]
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

    private fun getRandomTop(): Float {
        val height = ScreenUtils.getScreenWidth(context).toFloat()
        val random = Random(System.nanoTime())
        val topY = ScreenUtils.dpToPx(context, 50.0f)
        val bottomY = height / 3 - ScreenUtils.dpToPx(context, 10.0f)
        var randomY = height * random.nextFloat()
        while (randomY <= topY || randomY >= bottomY) {
            randomY = height * random.nextFloat()
        }
        return randomY
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // Draw enemies using legacy map (for collision compat)
        enemiesMap.forEach { data ->
            data.value?.let { initializeEnemies(it, data.key, canvas) }
        }

        // Draw enemy bullets and destroyed enemies
        drawEnemyBullets(canvas)
        drawDestroyedEnemies(canvas)
        removeExpiredEnemies()
    }

    private fun initializeEnemies(originBitmap: Bitmap, left: Float, canvas: Canvas) {
        val enemyBitmap = BitmapUtils.resizeBitmap(
            originBitmap,
            ScreenUtils.dpToPx(context, 48.0f),
            ScreenUtils.dpToPx(context, 48.0f),
            180.0F
        )
        if (enemyBitmap != null) {
            enemyY += getEnemyMoveSpeed() * speed
            if (enemyY > ScreenUtils.getScreenHeight(context).toFloat()) {
                enemyY = 0F
                refreshData()
            }
            canvas.drawBitmap(enemyBitmap, left, enemyY, mPaint)
        }
    }

    private fun drawEnemyBullets(canvas: Canvas) {
        val screenHeight = ScreenUtils.getScreenHeight(context).toFloat()
        val currentBulletSpeed = getEnemyBulletSpeed()
        bulletBitmap?.let { bmp ->
            for (enemy in enemyStates) {
                if (enemy.isDestroyed()) continue
                // Fire bullet periodically: add new bullet when list is empty or last bullet is far enough
                val enemyCenterX = enemy.x + ScreenUtils.dpToPx(context, 24.0f)
                if (enemy.bullets.isEmpty() || (enemy.bullets.last() - enemyY) > ScreenUtils.dpToPx(context, 150.0f)) {
                    enemy.bullets.add(enemyY + ScreenUtils.dpToPx(context, 48.0f))
                }
                // Update and draw each bullet
                val iter = enemy.bullets.iterator()
                while (iter.hasNext()) {
                    val bulletY = iter.next()
                    val newY = bulletY + currentBulletSpeed * speed
                    if (newY > screenHeight) {
                        iter.remove()
                    } else {
                        enemy.bullets[enemy.bullets.indexOf(bulletY)] = newY
                        canvas.drawBitmap(bmp, enemyCenterX - bmp.width / 2, newY, mPaint)
                    }
                }
            }
        }
    }

    private fun drawDestroyedEnemies(canvas: Canvas) {
        val destroyPaint = Paint().apply {
            color = Color.RED
            alpha = 128
            style = Paint.Style.FILL
        }
        for (enemy in enemyStates) {
            if (enemy.isDestroyed() && !enemy.isExpired()) {
                val size = ScreenUtils.dpToPx(context, 48.0f).toFloat()
                canvas.drawCircle(
                    enemy.x + size / 2,
                    enemyY + size / 2,
                    size / 2,
                    destroyPaint
                )
            }
        }
    }

    private fun removeExpiredEnemies() {
        enemyStates.removeAll { it.isExpired() }
    }

    fun getEnemyBullets(): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()
        bulletBitmap?.let { bmp ->
            for (enemy in enemyStates) {
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

    fun hitEnemy(enemyX: Float): EnemyState? {
        for (enemy in enemyStates) {
            if (!enemy.isDestroyed() && enemy.x == enemyX) {
                enemy.health -= 20f
                if (enemy.isDestroyed()) {
                    enemy.destroyedTime = System.currentTimeMillis()
                    // Remove from the legacy map so it stops rendering as alive
                    enemiesMap.remove(enemyX)
                }
                return enemy
            }
        }
        return null
    }

    private fun refreshData() {
        enemiesMap.clear()
        enemyStates.clear()
        val random = Random(System.nanoTime())
        var numberOfEnemies: Int = random.nextInt() % 10
        while (numberOfEnemies < 2 || numberOfEnemies > 8) {
            numberOfEnemies = random.nextInt() % 10
        }
        for (i in 1..numberOfEnemies) {
            val x = getRandomLeft()
            val bmp = getRandomEnemyBitmap()
            enemiesMap[x] = bmp
            enemyStates.add(EnemyState(x = x, bitmap = bmp))
        }
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
