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
import com.young.aircraft.data.BossBomb
import com.young.aircraft.data.BossState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

class BossEnemy(var context: Context, var speed: Float) : DrawBaseObject(context) {
    var activeBoss: BossState? = null
    private val deathExplosions = mutableListOf<ExplosionEffect>()
    private val bombExplosions = mutableListOf<ExplosionEffect>()
    var level: Int = 1

    private val bossBitmaps = mutableListOf<Bitmap?>()
    private val missileBitmaps = mutableListOf<Bitmap?>()

    val bossSizePx: Int = ScreenUtils.dpToPx(context, 350.0f)
    val missileSizePx: Int = ScreenUtils.dpToPx(context, 90.0f)
    private val screenDensity: Int = context.resources.displayMetrics.densityDpi
    private val screenWidth: Float = ScreenUtils.getScreenWidth(context).toFloat()
    private val screenHeight: Float = ScreenUtils.getScreenHeight(context).toFloat()

    // Density-scaled rendered sizes (updated each frame in onDraw)
    var renderedBossSize: Float = bossSizePx.toFloat()
        private set
    var renderedMissileSize: Float = missileSizePx.toFloat()
        private set

    // Movement AI
    private var moveDirectionX: Float = 1f
    private var directionChangeCounter: Int = 0
    private var directionChangeInterval: Int = 90
    private val rng = Random(System.nanoTime())

    // Bomb firing
    private var bombCounter: Int = 0

    // Player tracking
    var playerCenterX: Float = screenWidth / 2f

    // Freeze state (when true, boss and bombs don't move)
    var frozen: Boolean = false

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
        const val BASE_HP = 1000f
        const val DAMAGE_PER_HIT = 10f
        const val SPEED_MULTIPLIER = 1.5f
        const val BOMB_SPEED = 8f
        const val BASE_BOMB_FIRE_INTERVAL = 80
        const val HIT_FLASH_MS = 150L
        const val TARGET_ZONE_TOP = 0.08f
        const val TARGET_ZONE_BOTTOM = 0.30f
        const val COLLISION_INSET_X = 0.25f  // 25% inset on each side horizontally
        const val COLLISION_INSET_Y = 0.20f  // 20% inset on each side vertically
    }

    init {
        val bossResIds = intArrayOf(
            R.drawable.boss_1, R.drawable.boss_2, R.drawable.boss_3,
            R.drawable.boss_4, R.drawable.boss_5
        )
        for (resId in bossResIds) {
            val bmp = BitmapUtils.resizeBitmap(
                BitmapUtils.readBitMap(context, resId),
                bossSizePx, bossSizePx
            )
            bmp?.density = screenDensity
            bossBitmaps.add(bmp)
        }

        val missileResIds = intArrayOf(
            R.drawable.missile_1, R.drawable.missile_2, R.drawable.missile_3
        )
        for (resId in missileResIds) {
            val bmp = BitmapUtils.resizeBitmap(
                BitmapUtils.readBitMap(context, resId),
                missileSizePx, missileSizePx
            )
            bmp?.density = screenDensity
            missileBitmaps.add(bmp)
        }
    }

    fun getBossHp(level: Int): Float = BASE_HP + 100f * (level - 1)

    private fun getBombFireInterval(): Int {
        return (BASE_BOMB_FIRE_INTERVAL / (1f + 0.3f * (level - 1))).toInt().coerceAtLeast(15)
    }

    fun spawnBoss(level: Int) {
        this.level = level
        val hp = getBossHp(level)
        val bmpIndex = rng.nextInt(bossBitmaps.size)
        activeBoss = BossState(
            x = screenWidth / 2f - renderedBossSize / 2f,
            y = -renderedBossSize,
            hitPoints = hp,
            maxHitPoints = hp,
            bitmapIndex = bmpIndex
        )
        directionChangeCounter = 0
        moveDirectionX = if (rng.nextBoolean()) 1f else -1f
        directionChangeInterval = 60 + rng.nextInt(60)
        bombCounter = 0
    }

    fun isBossActive(): Boolean {
        val boss = activeBoss ?: return false
        return !boss.isDestroyed()
    }

    fun isBossDefeated(): Boolean {
        val boss = activeBoss ?: return false
        return boss.isDestroyed()
    }

    fun isBossExplosionFinished(): Boolean {
        val boss = activeBoss ?: return false
        return boss.isDestroyed() && boss.isExpired()
    }

    fun hitBoss(): Boolean {
        val boss = activeBoss ?: return false
        if (boss.isDestroyed()) return false
        boss.hitPoints -= DAMAGE_PER_HIT
        boss.lastHitTime = System.currentTimeMillis()
        if (boss.isDestroyed()) {
            boss.destroyedTime = System.currentTimeMillis()
            triggerBossExplosion(boss)
            return true
        }
        return false
    }

    fun getBossBounds(): RectF? {
        val boss = activeBoss ?: return null
        if (boss.isDestroyed()) return null
        val insetX = renderedBossSize * COLLISION_INSET_X
        val insetY = renderedBossSize * COLLISION_INSET_Y
        return RectF(
            boss.x + insetX,
            boss.y + insetY,
            boss.x + renderedBossSize - insetX,
            boss.y + renderedBossSize - insetY
        )
    }

    fun getBombBounds(bomb: BossBomb): RectF {
        return RectF(bomb.x, bomb.y, bomb.x + renderedMissileSize, bomb.y + renderedMissileSize)
    }

    fun removeBomb(bomb: BossBomb) {
        activeBoss?.bombs?.remove(bomb)
    }

    fun triggerBombExplosion(centerX: Float, centerY: Float) {
        val blastSize = minOf(screenWidth, screenHeight) * 0.20f
        bombExplosions.add(
            ExplosionEffect(
                centerX = centerX,
                centerY = centerY,
                size = blastSize,
                scale = 1.5f
            )
        )
    }

    private fun getMovementSpeed(): Float {
        val enemyBaseSpeed = 3f + (level - 1) * 1.5f
        return enemyBaseSpeed * SPEED_MULTIPLIER
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // Compute density scale matching Aircraft.kt / Enemies.kt pattern
        val scale = if (canvas.density > 0) canvas.density.toFloat() / screenDensity else 1f
        renderedBossSize = bossSizePx * scale
        renderedMissileSize = missileSizePx * scale

        val boss = activeBoss
        if (boss != null) {
            if (!boss.isDestroyed()) {
                updateBossMovement(boss)
                updateBombs(boss)
                drawBoss(canvas, boss)
                drawBombs(canvas, boss)
            } else {
                // Boss destroyed - draw remaining bombs drifting
                drawBombs(canvas, boss)
            }
        }
        drawDeathExplosions(canvas)
        drawBombExplosions(canvas)
    }

    private val marginPx = ScreenUtils.dpToPx(context, 40.0f).toFloat()

    private fun updateBossMovement(boss: BossState) {
        // Don't move if frozen
        if (frozen) return

        val moveSpeed = getMovementSpeed() * speed
        val targetMinY = screenHeight * TARGET_ZONE_TOP
        val targetMaxY = screenHeight * TARGET_ZONE_BOTTOM

        // Vertical: advance into target zone, retreat if too far down
        when {
            boss.y < targetMinY -> {
                // Above zone - advance down
                boss.y += moveSpeed
            }
            boss.y > targetMaxY -> {
                // Below zone - retreat up
                boss.y -= moveSpeed * 0.7f
            }
            else -> {
                // In zone - small vertical drift
                val drift = (rng.nextFloat() - 0.5f) * moveSpeed * 0.3f
                boss.y += drift
                boss.y = boss.y.coerceIn(targetMinY, targetMaxY)
            }
        }

        // Horizontal: track player position using rendered size
        val bossCenterX = boss.x + renderedBossSize / 2f
        moveDirectionX = if (playerCenterX < bossCenterX) -1f else 1f

        boss.x += moveSpeed * moveDirectionX * 0.8f

        // Bounce off screen edges using rendered size
        if (boss.x < marginPx) {
            boss.x = marginPx
            moveDirectionX = 1f
        }
        if (boss.x + renderedBossSize > screenWidth - marginPx) {
            boss.x = screenWidth - marginPx - renderedBossSize
            moveDirectionX = -1f
        }
    }

    private fun updateBombs(boss: BossState) {
        if (boss.isDestroyed()) return
        // Don't fire new bombs if frozen
        if (frozen) return

        // Only fire when boss is in the target zone (visible on screen)
        if (boss.y >= screenHeight * TARGET_ZONE_TOP) {
            bombCounter++
            if (bombCounter >= getBombFireInterval()) {
                bombCounter = 0
                fireBomb(boss)
            }
        }
    }

    private fun fireBomb(boss: BossState) {
        val bmpIndex = rng.nextInt(missileBitmaps.size)
        // Launch from the bottom-center of the rendered boss sprite
        val bombX = boss.x + renderedBossSize / 2f - renderedMissileSize / 2f
        val bombY = boss.y + renderedBossSize
        boss.bombs.add(BossBomb(x = bombX, y = bombY, bitmapIndex = bmpIndex))
    }

    private fun drawBoss(canvas: Canvas, boss: BossState) {
        val bmp = bossBitmaps.getOrNull(boss.bitmapIndex) ?: return
        val now = System.currentTimeMillis()
        val paint = if (now - boss.lastHitTime < HIT_FLASH_MS) hitFlashPaint else mPaint
        canvas.drawBitmap(bmp, boss.x, boss.y, paint)
    }

    private fun drawBombs(canvas: Canvas, boss: BossState) {
        val bombSpeed = if (frozen) 0f else BOMB_SPEED
        val iter = boss.bombs.iterator()
        while (iter.hasNext()) {
            val bomb = iter.next()
            bomb.y += bombSpeed * speed
            // Remove if off-screen bottom
            if (bomb.y > screenHeight) {
                iter.remove()
                continue
            }
            val bmp = missileBitmaps.getOrNull(bomb.bitmapIndex)
            bmp?.let { canvas.drawBitmap(it, bomb.x, bomb.y, mPaint) }
        }
    }

    private fun triggerBossExplosion(boss: BossState) {
        val centerX = boss.x + renderedBossSize / 2f
        val centerY = boss.y + renderedBossSize / 2f
        val size = renderedBossSize

        // Main massive explosion
        deathExplosions.add(ExplosionEffect(centerX, centerY, size * 2f, scale = 3f))
        // Secondary staggered explosions around the boss
        deathExplosions.add(
            ExplosionEffect(centerX - size * 0.4f, centerY - size * 0.3f, size, scale = 2f)
        )
        deathExplosions.add(
            ExplosionEffect(centerX + size * 0.4f, centerY + size * 0.2f, size, scale = 2f)
        )
        deathExplosions.add(
            ExplosionEffect(centerX - size * 0.2f, centerY + size * 0.4f, size * 0.8f, scale = 1.5f)
        )
        deathExplosions.add(
            ExplosionEffect(centerX + size * 0.3f, centerY - size * 0.4f, size * 0.8f, scale = 1.5f)
        )
    }

    private fun drawDeathExplosions(canvas: Canvas) {
        val iter = deathExplosions.iterator()
        while (iter.hasNext()) {
            val explosion = iter.next()
            if (explosion.isFinished()) {
                iter.remove()
            } else {
                explosion.draw(canvas)
            }
        }
    }

    private fun drawBombExplosions(canvas: Canvas) {
        val iter = bombExplosions.iterator()
        while (iter.hasNext()) {
            val explosion = iter.next()
            if (explosion.isFinished()) {
                iter.remove()
            } else {
                explosion.draw(canvas)
            }
        }
    }

    fun clearAll() {
        activeBoss = null
        deathExplosions.clear()
        bombExplosions.clear()
        bombCounter = 0
        directionChangeCounter = 0
    }

    override fun updateGame() {}

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
