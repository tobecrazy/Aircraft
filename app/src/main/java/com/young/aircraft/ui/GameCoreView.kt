package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.service.MusicService
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.utils.ScreenUtils
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import com.young.aircraft.data.PlayerAircraft as AircraftData


/**
 * Create by Young
 **/
class GameCoreView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private lateinit var gameThread: Thread
    lateinit var drawBackground: DrawBackground
    lateinit var drawHeader: DrawHeader
    lateinit var drawAircraft: Aircraft
    lateinit var enemies: Enemies
    lateinit var redEnvelopes: RedEnvelopes
    lateinit var bossEnemy: BossEnemy
    lateinit var medicalKits: MedicalKits
    lateinit var shields: Shields
    lateinit var timeFreezes: TimeFreezes
    lateinit var playerData: AircraftData
    private var surfaceHolder: SurfaceHolder? = null
    private var collisionCooldown = false
    private var gameInitialized = false
    var musicService: MusicService? = null
    var onGameOver: (() -> Unit)? = null
    var onGameWon: (() -> Unit)? = null
    var onLevelComplete: ((Int) -> Unit)? = null
    var level: Int = 1
    var jetPlaneResId: Int = R.drawable.jet_plane_2
    var jetPlaneIndex: Int = 0
    var levelStartTimeMs: Long = 0L
    var enemiesDestroyedThisLevel: Int = 0
    var totalKills: Int = 0
    private var gameWon = false
    private var isPaused = false
    private var bossDefeatedThisLevel = false

    // Background pause tracking (for timer adjustment on surface recreation)
    private var pausedAtMs: Long = 0L

    // Screen shake state
    private var shakeStartTimeMs: Long = 0L
    private val shakeRng = Random(System.nanoTime())

    // Damage flash state
    private var damageFlashStartMs: Long = 0L
    private val damageFlashPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    // Low-health vignette
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Death explosion
    private var playerDeathExplosion: ExplosionEffect? = null
    private var isPlayerDying = false
    private val settingsRepository = SettingsRepository(context)

    // Vibrator
    private val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    companion object {
        const val FPS: Int = 30
        const val MAX_LEVEL = 10
        const val SHAKE_DURATION_MS = 300L
        const val SHAKE_MAX_OFFSET_DP = 8f
        const val FLASH_DURATION_MS = 300L
        const val HIT_VIBRATION_MS = 1000L
        fun getLevelDurationMs(level: Int): Long = (300_000L - 20_000L * (level - 1))
        fun getRequiredKills(level: Int): Int = 90 + level * 10
        fun calculateFrameSleepTimeMs(targetFrameTimeMs: Long, frameDurationMs: Long): Long {
            return (targetFrameTimeMs - frameDurationMs).coerceAtLeast(0L)
        }

        fun calculateDroppedFrames(targetFrameTimeMs: Long, frameDurationMs: Long): Int {
            if (targetFrameTimeMs !in 1..<frameDurationMs) {
                return 0
            }
            return ((frameDurationMs - targetFrameTimeMs) / targetFrameTimeMs).toInt()
        }
    }

    init {
        surfaceHolder = holder
        surfaceHolder?.addCallback(this)
        focusable = FOCUSABLE
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!gameInitialized) {
            initializeGameDrawer()
        } else {
            // Resuming from background — shift level timer forward to exclude time spent in background
            if (pausedAtMs > 0L) {
                levelStartTimeMs += System.currentTimeMillis() - pausedAtMs
                pausedAtMs = 0L
            }
        }
        isRunning = true
        gameThread = Thread(this)
        gameThread.start()
    }

    private fun initializeGameDrawer() {
        GameStateManager.isInvincible = settingsRepository.isInvincibleModeEnabled()
        val fireRateMultiplier = settingsRepository.getDifficulty().fireRateMultiplier
        
        // Resolve jetPlaneResId from index if possible
        if (jetPlaneIndex in Aircraft.JET_PLANES.indices) {
            jetPlaneResId = Aircraft.JET_PLANES[jetPlaneIndex]
        }
        
        drawBackground = DrawBackground(context, 2.0F)
        drawAircraft = Aircraft(context, 1.0F, jetPlaneResId, fireRateMultiplier)
        enemies = Enemies(context, 1.0F)
        enemies.level = level
        redEnvelopes = RedEnvelopes(context, 1.0F)
        redEnvelopes.level = level
        bossEnemy = BossEnemy(context, 1.0F)
        bossEnemy.level = level
        medicalKits = MedicalKits(context, 1.0F)
        medicalKits.level = level
        shields = Shields(context, 1.0F)
        shields.level = level
        timeFreezes = TimeFreezes(context, 1.0F)
        timeFreezes.level = level
        playerData = AircraftData(name = "Player", health_points = 100.0f)
        drawHeader = DrawHeader(context, playerData, this)
        levelStartTimeMs = System.currentTimeMillis()
        enemiesDestroyedThisLevel = 0
        gameInitialized = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    private fun checkCollision() {
        val aircraftBounds = drawAircraft.getBounds()

        // Check player aircraft colliding with enemies
        for (enemy in enemies.activeEnemies) {
            if (enemy.isDestroyed()) continue
            val enemyBounds = enemies.getEnemyBounds(enemy)
            if (RectF.intersects(aircraftBounds, enemyBounds)) {
                if (!collisionCooldown) {
                    Log.d("Collision", "Aircraft collided with an enemy!")
                    handleCollision()
                }
            } else {
                collisionCooldown = false
            }
        }

        // Check enemy bullets hitting the player
        checkEnemyBulletsHitPlayer(aircraftBounds)

        // Check player bullets hitting enemies
        checkPlayerBulletsHitEnemies()

        // Check player bullets hitting red envelopes
        checkPlayerBulletsHitRedEnvelopes()

        // Check rockets hitting enemies
        checkRocketsHitEnemies()

        // Boss collision checks
        checkPlayerVsBoss(aircraftBounds)
        checkBossBombsHitPlayer(aircraftBounds)
        checkPlayerBulletsHitBoss()
        checkRocketsHitBoss()

        // Medical kit pickup
        checkMedicalKitPickup(aircraftBounds)

        // Shield pickup (player bullets hitting shields)
        checkPlayerBulletsHitShields()

        // Time freeze pickup (player or enemy touching)
        checkTimeFreezePickup(aircraftBounds)
    }

    private fun checkEnemyBulletsHitPlayer(aircraftBounds: RectF) {
        enemies.forEachActiveBullet { enemy, bx, bulletRef ->
            val bulletBounds = enemies.getBulletBounds(bx, bulletRef.y)
            if (RectF.intersects(aircraftBounds, bulletBounds)) {
                enemy.bullets.remove(bulletRef)
                if (drawAircraft.isShielded()) {
                    Log.d("Game", "Shield absorbed enemy bullet!")
                    return@forEachActiveBullet true
                }
                playerData.hit()
                musicService?.playerHitSoundPlay()
                triggerHitEffects()
                Log.d("Game", "Player hit by enemy bullet! HP: ${playerData.health_points}")
                if (!playerData.isAlive()) {
                    musicService?.gameOverSoundPlay()
                    triggerDeathExplosion()
                    Log.d("Game", "Game Over!")
                }
                return@forEachActiveBullet true
            }
            false
        }
    }

    private fun checkPlayerBulletsHitEnemies() {
        val bullets = drawAircraft.getBullets()
        val enemySize = enemies.enemySizePx
        val bulletW = drawAircraft.bulletWidthPx
        val bulletH = drawAircraft.bulletHeightPx

        for (bullet in bullets) {
            if (bullet.y < 0) continue
            val bulletBounds = RectF(
                bullet.x, bullet.y,
                bullet.x + bulletW,
                bullet.y + bulletH
            )

            for (enemy in enemies.activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyBounds = RectF(
                    enemy.x, enemy.y,
                    enemy.x + enemySize, enemy.y + enemySize
                )
                if (RectF.intersects(bulletBounds, enemyBounds)) {
                    enemies.hitEnemy(enemy)
                    drawAircraft.removeBullet(bullet)
                    enemiesDestroyedThisLevel++
                    totalKills++
                    musicService?.enemyHitSoundPlay()
                    Log.d("Game", "Enemy destroyed! Kills: $enemiesDestroyedThisLevel")
                    checkKillTarget()
                    break
                }
            }
        }
    }

    private fun checkPlayerBulletsHitRedEnvelopes() {
        val bullets = drawAircraft.getBullets().toList()
        val bulletW = drawAircraft.bulletWidthPx
        val bulletH = drawAircraft.bulletHeightPx

        for (bullet in bullets) {
            if (bullet.y < 0) continue
            val bulletBounds = RectF(
                bullet.x, bullet.y,
                bullet.x + bulletW,
                bullet.y + bulletH
            )

            for (envelope in redEnvelopes.activeEnvelopes) {
                if (envelope.isDetonated()) continue
                val envBounds = redEnvelopes.getEnvelopeBounds(envelope)
                if (RectF.intersects(bulletBounds, envBounds)) {
                    drawAircraft.removeBullet(bullet)
                    val detonated = redEnvelopes.hitEnvelope(envelope)
                    if (detonated) {
                        redEnvelopes.launchRocket(
                            drawAircraft.jetX,
                            drawAircraft.jetY,
                            drawAircraft.renderedJetW
                        )
                    }
                    break
                }
            }
        }
    }

    private fun checkRocketsHitEnemies() {
        val enemySize = ScreenUtils.dpToPx(context, 48.0f)
        val blastSide = min(
            ScreenUtils.getScreenWidth(context).toFloat(),
            ScreenUtils.getScreenHeight(context).toFloat()
        ) * 0.20f

        for (rocket in redEnvelopes.activeRockets) {
            if (!rocket.active) continue
            val rocketBounds = redEnvelopes.getRocketBounds(rocket)

            for (enemy in enemies.activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyBounds = RectF(
                    enemy.x, enemy.y,
                    enemy.x + enemySize, enemy.y + enemySize
                )
                if (RectF.intersects(rocketBounds, enemyBounds)) {
                    // First hit: deactivate rocket, AoE blast
                    rocket.active = false
                    val impactX = enemy.x + enemySize / 2f
                    val impactY = enemy.y + enemySize / 2f
                    redEnvelopes.triggerRocketExplosion(impactX, impactY)

                    // Blast area centered on impact
                    val halfBlast = blastSide / 2f
                    val blastRect = RectF(
                        impactX - halfBlast, impactY - halfBlast,
                        impactX + halfBlast, impactY + halfBlast
                    )

                    // Destroy all enemies in blast radius
                    for (target in enemies.activeEnemies) {
                        if (target.isDestroyed()) continue
                        val targetBounds = RectF(
                            target.x, target.y,
                            target.x + enemySize, target.y + enemySize
                        )
                        if (RectF.intersects(blastRect, targetBounds)) {
                            enemies.hitEnemy(target)
                            enemiesDestroyedThisLevel++
                            totalKills++
                            musicService?.enemyHitSoundPlay()
                        }
                    }
                    checkKillTarget()
                    break
                }
            }
        }
    }

    private fun handleCollision() {
        if (drawAircraft.isShielded()) {
            collisionCooldown = true
            return
        }
        playerData.hit()
        collisionCooldown = true
        musicService?.playerHitSoundPlay()
        triggerHitEffects()
        Log.d("Game", "Player hit! HP: ${playerData.health_points}")
        if (!playerData.isAlive()) {
            musicService?.gameOverSoundPlay()
            triggerDeathExplosion()
            Log.d("Game", "Game Over!")
        }
    }

    private fun checkKillTarget() {
        if (enemiesDestroyedThisLevel < getRequiredKills(level)) return

        // Kill target reached — spawn boss if not already active or defeated
        if (!bossEnemy.isBossActive() && !bossEnemy.isBossDefeated() && !bossDefeatedThisLevel) {
            bossEnemy.spawnBoss(level)
            enemies.spawnPaused = true
            Log.d("Game", "Boss spawned at level $level!")
        }
    }

    private fun checkBossDefeated() {
        if (bossDefeatedThisLevel) return
        if (!bossEnemy.isBossExplosionFinished()) return

        bossDefeatedThisLevel = true
        Log.d("Game", "Boss defeated at level $level!")

        if (level >= MAX_LEVEL) {
            gameWon = true
            isPaused = true
            Log.d("Game", "Game Won! All levels cleared!")
            post { onGameWon?.invoke() }
        } else {
            isPaused = true
            Log.d("Game", "Level $level complete!")
            post { onLevelComplete?.invoke(level) }
        }
    }

    private fun checkPlayerVsBoss(aircraftBounds: RectF) {
        val bossBounds = bossEnemy.getBossBounds() ?: return
        if (RectF.intersects(aircraftBounds, bossBounds)) {
            if (drawAircraft.isShielded()) {
                Log.d("Game", "Shield absorbed boss collision!")
                return
            }
            // Instant death on boss collision
            playerData.health_points = 0f
            musicService?.gameOverSoundPlay()
            triggerDeathExplosion()
            Log.d("Game", "Player collided with Boss — instant death!")
        }
    }

    private val proximityPx = ScreenUtils.dpToPx(context, 5.0f).toFloat()
    private val maxOffsetPx = ScreenUtils.dpToPx(context, SHAKE_MAX_OFFSET_DP).toFloat()

    private fun checkBossBombsHitPlayer(aircraftBounds: RectF) {
        val boss = bossEnemy.activeBoss ?: return
        val expandedBounds = RectF(
            aircraftBounds.left - proximityPx,
            aircraftBounds.top - proximityPx,
            aircraftBounds.right + proximityPx,
            aircraftBounds.bottom + proximityPx
        )

        val bombIter = boss.bombs.iterator()
        while (bombIter.hasNext()) {
            val bomb = bombIter.next()
            val bombCenterX = bomb.x + bossEnemy.renderedMissileSize / 2f
            val bombCenterY = bomb.y + bossEnemy.renderedMissileSize / 2f
            if (expandedBounds.contains(bombCenterX, bombCenterY)) {
                bombIter.remove()
                bossEnemy.triggerBombExplosion(bombCenterX, bombCenterY)
                if (drawAircraft.isShielded()) {
                    Log.d("Game", "Shield absorbed boss bomb!")
                    break
                }
                playerData.hit()
                musicService?.playerHitSoundPlay()
                triggerHitEffects()
                Log.d("Game", "Player hit by boss bomb! HP: ${playerData.health_points}")
                if (!playerData.isAlive()) {
                    musicService?.gameOverSoundPlay()
                    triggerDeathExplosion()
                    Log.d("Game", "Game Over!")
                }
                break
            }
        }
    }

    private fun checkPlayerBulletsHitBoss() {
        if (!bossEnemy.isBossActive()) return
        val bullets = drawAircraft.getBullets()
        val bulletSize = ScreenUtils.dpToPx(context, 40.0f)
        val bossBounds = bossEnemy.getBossBounds() ?: return

        for (bullet in bullets) {
            if (bullet.y < 0) continue
            val bulletBounds = RectF(
                bullet.x, bullet.y,
                bullet.x + bulletSize,
                bullet.y + bulletSize
            )
            if (RectF.intersects(bulletBounds, bossBounds)) {
                drawAircraft.removeBullet(bullet)
                val killed = bossEnemy.hitBoss()
                musicService?.enemyHitSoundPlay()
                if (killed) {
                    Log.d("Game", "Boss killed!")
                }
                break
            }
        }
    }

    private fun checkRocketsHitBoss() {
        if (!bossEnemy.isBossActive()) return
        val bossBounds = bossEnemy.getBossBounds() ?: return

        for (rocket in redEnvelopes.activeRockets) {
            if (!rocket.active) continue
            val rocketBounds = redEnvelopes.getRocketBounds(rocket)
            if (RectF.intersects(rocketBounds, bossBounds)) {
                rocket.active = false
                val impactX = bossBounds.centerX()
                val impactY = bossBounds.centerY()
                redEnvelopes.triggerRocketExplosion(impactX, impactY)
                bossEnemy.hitBoss()
                musicService?.enemyHitSoundPlay()
                Log.d("Game", "Rocket hit boss!")
                break
            }
        }
    }

    private fun checkMedicalKitPickup(aircraftBounds: RectF) {
        for (kit in medicalKits.activeKits) {
            if (kit.collected) continue
            val kitBounds = medicalKits.getKitBounds(kit)

            // Player gets priority
            if (!playerData.isFullHealth() && RectF.intersects(aircraftBounds, kitBounds)) {
                kit.collected = true
                playerData.restoreHealth()
                Log.d("Game", "Player picked up medical kit! HP restored to max.")
                continue
            }

            // Boss check
            val boss = bossEnemy.activeBoss ?: continue
            if (boss.isDestroyed()) continue
            if (boss.hitPoints >= boss.maxHitPoints) continue
            val bossBounds = bossEnemy.getBossBounds() ?: continue
            if (RectF.intersects(bossBounds, kitBounds)) {
                kit.collected = true
                boss.hitPoints = boss.maxHitPoints
                Log.d("Game", "Boss picked up medical kit! Boss HP restored to max.")
            }
        }
    }

    private fun checkPlayerBulletsHitShields() {
        val bullets = drawAircraft.getBullets()
        val bulletSize = ScreenUtils.dpToPx(context, 40.0f)

        for (bullet in bullets) {
            if (bullet.y < 0) continue
            val bulletBounds = RectF(
                bullet.x, bullet.y,
                bullet.x + bulletSize,
                bullet.y + bulletSize
            )

            for (shield in shields.activeShields) {
                if (shield.collected) continue
                val shieldBounds = shields.getShieldBounds(shield)
                if (RectF.intersects(bulletBounds, shieldBounds)) {
                    drawAircraft.removeBullet(bullet)
                    shield.collected = true
                    drawAircraft.activateShield()
                    Log.d("Game", "Shield collected! Player is invincible for 10 seconds.")
                    break
                }
            }
        }
    }

    private fun checkTimeFreezePickup(aircraftBounds: RectF) {
        val enemySize = ScreenUtils.dpToPx(context, 48.0f)

        for (timeFreeze in timeFreezes.activeTimeFreezes) {
            if (timeFreeze.collected) continue
            val freezeBounds = timeFreezes.getTimeFreezeBounds(timeFreeze)

            // Check if player aircraft touches the time freeze
            if (RectF.intersects(aircraftBounds, freezeBounds)) {
                timeFreezes.collectByPlayer(timeFreeze)
                Log.d("Game", "Player collected time freeze! Enemies frozen for 5 seconds.")
                continue
            }

            // Check if any enemy touches the time freeze
            for (enemy in enemies.activeEnemies) {
                if (enemy.isDestroyed()) continue
                val enemyBounds = RectF(
                    enemy.x, enemy.y,
                    enemy.x + enemySize, enemy.y + enemySize
                )
                if (RectF.intersects(enemyBounds, freezeBounds)) {
                    timeFreezes.collectByEnemy(timeFreeze)
                    Log.d("Game", "Enemy collected time freeze! Player frozen for 5 seconds.")
                    break
                }
            }
        }
    }

    fun advanceToNextLevel() {
        level++
        enemies.level = level
        enemies.activeEnemies.clear()
        enemies.clearExplosions()
        enemies.spawnPaused = false
        redEnvelopes.level = level
        redEnvelopes.clearAll()
        bossEnemy.level = level
        bossEnemy.clearAll()
        medicalKits.level = level
        medicalKits.clearAll()
        shields.level = level
        shields.clearAll()
        timeFreezes.level = level
        timeFreezes.clearAll()
        drawAircraft.shieldEndTimeMs = 0L
        bossDefeatedThisLevel = false
        drawBackground.randomizeBackground()
        levelStartTimeMs = System.currentTimeMillis()
        enemiesDestroyedThisLevel = 0
        isPaused = false
        Log.d("Game", "Advanced to level $level")
    }

    private fun checkLevelTimer() {
        val elapsed = System.currentTimeMillis() - levelStartTimeMs
        if (elapsed < getLevelDurationMs(level)) return

        // Don't time out if boss is active or defeated (boss fight in progress)
        if (bossEnemy.isBossActive() || bossEnemy.isBossDefeated()) return

        // Time expired without meeting kill target → game over
        musicService?.gameOverSoundPlay()
        isRunning = false
        Log.d("Game", "Level failed! Kills: $enemiesDestroyedThisLevel/${getRequiredKills(level)}")
        post { onGameOver?.invoke() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pausedAtMs = System.currentTimeMillis()
        var isRetry = true
        while (isRetry) {
            try {
                isRunning = false
                gameThread.join()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isRetry = false
        }
    }

    private fun onUpdateGameDraw(canvas: Canvas?) {
        if (null == canvas) return

        // Screen shake
        val shaking = applyScreenShake(canvas)

        drawBackground(canvas)

        // Skip drawing aircraft if dying (explosion replaces it)
        if (!isPlayerDying) {
            drawAircraft(canvas)
        }

        if (!isPaused && !isPlayerDying) {
            checkLevelTimer()
            drawEnemies(canvas)
            drawRedEnvelopes(canvas)
            drawMedicalKits(canvas)
            drawShields(canvas)
            drawTimeFreezes(canvas)
            drawBossEnemy(canvas)
            checkCollision()
            checkBossDefeated()
        } else {
            // Still draw boss explosions when paused (boss death animation)
            drawBossEnemy(canvas)
        }

        // Death explosion (drawn over everything except overlays)
        drawDeathExplosion(canvas)

        // Overlays
        drawDamageFlash(canvas)
        drawLowHealthVignette(canvas)

        // HUD drawn last so it is never obscured by game objects or overlays
        drawHeader(canvas)

        // Restore shake offset
        if (shaking) {
            canvas.restore()
        }
    }

    private fun applyScreenShake(canvas: Canvas): Boolean {
        val elapsed = System.currentTimeMillis() - shakeStartTimeMs
        if (elapsed >= SHAKE_DURATION_MS) return false

        val decay = 1f - elapsed.toFloat() / SHAKE_DURATION_MS
        val offsetX = (shakeRng.nextFloat() * 2f - 1f) * maxOffsetPx * decay
        val offsetY = (shakeRng.nextFloat() * 2f - 1f) * maxOffsetPx * decay

        canvas.save()
        canvas.translate(offsetX, offsetY)
        return true
    }

    private fun drawDamageFlash(canvas: Canvas) {
        val elapsed = System.currentTimeMillis() - damageFlashStartMs
        if (elapsed >= FLASH_DURATION_MS) return

        val alpha = ((1f - elapsed.toFloat() / FLASH_DURATION_MS) * 80).toInt().coerceIn(0, 255)
        damageFlashPaint.alpha = alpha
        canvas.drawRect(
            0f, 0f,
            ScreenUtils.getScreenWidth(context).toFloat(),
            ScreenUtils.getScreenHeight(context).toFloat(),
            damageFlashPaint
        )
    }

    private fun drawLowHealthVignette(canvas: Canvas) {
        if (!gameInitialized) return
        if (playerData.health_points > 20f || !playerData.isAlive()) return

        val screenW = ScreenUtils.getScreenWidth(context).toFloat()
        val screenH = ScreenUtils.getScreenHeight(context).toFloat()
        val centerX = screenW / 2f
        val centerY = screenH / 2f
        val radius = maxOf(screenW, screenH) * 0.7f

        // Pulsing alpha: sin wave between 30 and 80
        val pulse = (sin(System.currentTimeMillis() / 300.0) * 0.5 + 0.5).toFloat()
        val alpha = (30 + pulse * 50).toInt()

        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 255, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        vignettePaint.shader = gradient
        canvas.drawRect(0f, 0f, screenW, screenH, vignettePaint)
        vignettePaint.shader = null
    }

    private fun drawDeathExplosion(canvas: Canvas) {
        val explosion = playerDeathExplosion ?: return
        if (explosion.isFinished()) {
            playerDeathExplosion = null
            isPlayerDying = false
            isRunning = false
            Log.d("Game", "Death explosion finished — Game Over!")
            post { onGameOver?.invoke() }
        } else {
            explosion.draw(canvas)
        }
    }

    private fun triggerHitEffects() {
        val now = System.currentTimeMillis()
        damageFlashStartMs = now
        drawAircraft.hitTimeMs = now
        if (settingsRepository.isHitShakeEffectEnabled()) {
            shakeStartTimeMs = now
            vibrator.vibrate(
                VibrationEffect.createOneShot(HIT_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    private fun triggerDeathExplosion() {
        isPlayerDying = true
        isPaused = true
        val jetCenterX = drawAircraft.jetX + drawAircraft.renderedJetW / 2f
        val jetCenterY = drawAircraft.jetY + drawAircraft.renderedJetH / 2f
        val explosionSize = maxOf(drawAircraft.renderedJetW, drawAircraft.renderedJetH)
        playerDeathExplosion = ExplosionEffect(
            centerX = jetCenterX,
            centerY = jetCenterY,
            size = explosionSize,
            scale = 2.5f
        )
    }

    private fun drawEnemies(canvas: Canvas) {
        // Update enemy frozen state based on time freeze
        enemies.frozen = timeFreezes.isEnemyFrozen()
        enemies.onDraw(canvas)
    }

    private fun drawRedEnvelopes(canvas: Canvas) {
        redEnvelopes.onDraw(canvas)
    }

    private fun drawMedicalKits(canvas: Canvas) {
        medicalKits.onDraw(canvas)
    }

    private fun drawShields(canvas: Canvas) {
        shields.onDraw(canvas)
    }

    private fun drawTimeFreezes(canvas: Canvas) {
        timeFreezes.onDraw(canvas)
    }

    private fun drawBossEnemy(canvas: Canvas) {
        bossEnemy.playerCenterX = drawAircraft.jetX + drawAircraft.renderedJetW / 2f
        // Update boss frozen state based on time freeze
        bossEnemy.frozen = timeFreezes.isEnemyFrozen()
        bossEnemy.onDraw(canvas)
    }

    private fun drawAircraft(canvas: Canvas) {
        // Update player frozen state based on time freeze
        drawAircraft.frozen = timeFreezes.isPlayerFrozen()
        drawAircraft.onDraw(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        drawBackground.onDraw(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        drawHeader.onDraw(canvas)
    }

    private var avg_FPS: Double = 0.0
    private var droppedFrameCount: Int = 0
    private var isRunning = true
    var canvas: Canvas? = null

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        var totalTime: Long = 0
        var frameCount: Int = 0
        val targetTime: Long = (1000 / FPS).toLong()
        while (isRunning) {
            startTime = System.nanoTime()
            try {
                canvas = surfaceHolder?.lockCanvas()
                if (null == canvas || null == surfaceHolder) return
                surfaceHolder?.let {
                    synchronized(it) {
                        onUpdateGameDraw(canvas)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    canvas?.let {
                        surfaceHolder?.unlockCanvasAndPost(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            timeMillis = (System.nanoTime() - startTime) / 1000000
            waitTime = calculateFrameSleepTimeMs(targetTime, timeMillis)
            droppedFrameCount += calculateDroppedFrames(targetTime, timeMillis)

            try {
                if (waitTime > 0L) {
                    Thread.sleep(waitTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            totalTime += System.nanoTime() - startTime
            frameCount++
            if (frameCount == FPS) {
                avg_FPS = (1000 / ((totalTime / frameCount) / 1000000)).toDouble()
                if (droppedFrameCount > 0) {
                    Log.w("GameCoreView", "Dropped frames in last second: $droppedFrameCount")
                }
                frameCount = 0
                totalTime = 0
                droppedFrameCount = 0
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (gameInitialized) {
                    val (renderedW, renderedH) = drawAircraft.getRenderedJetSize()
                    drawAircraft.jetX = event.x - renderedW / 2f
                    drawAircraft.jetY = event.y - renderedH / 2f
                }
            }
        }
        return true
    }

    fun pauseGame() {
        isPaused = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("YoungTest", "$event ---- $keyCode")
        drawAircraft.updateGame()
        return super.onKeyDown(keyCode, event)
    }
}
