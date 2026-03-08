package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.young.aircraft.data.Aircraft as AircraftData
import com.young.aircraft.service.MusicService
import com.young.aircraft.utils.ScreenUtils
import kotlin.math.abs


/**
 * Create by Young
 **/
class GameCoreView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private lateinit var gameThread: Thread
    lateinit var drawBackground: DrawBackground
    lateinit var drawHeader: DrawHeader
    lateinit var drawAircraft: Aircraft
    lateinit var enemies: Enemies
    lateinit var playerData: AircraftData
    private var surfaceHolder: SurfaceHolder? = null
    private var collisionCooldown = false
    private var gameInitialized = false
    var musicService: MusicService? = null
    var onGameOver: (() -> Unit)? = null
    var onGameWon: (() -> Unit)? = null
    var onLevelComplete: ((Int) -> Unit)? = null
    var level: Int = 1
    var levelStartTimeMs: Long = 0L
    var enemiesDestroyedThisLevel: Int = 0
    private var gameWon = false
    private var isPaused = false

    companion object {
        const val FPS: Int = 30
        const val MAX_LEVEL = 10
        fun getLevelDurationMs(level: Int): Long = (300_000L - 20_000L * (level - 1))
        fun getRequiredKills(level: Int): Int = 90 + level * 10
    }

    init {
        surfaceHolder = holder
        surfaceHolder?.addCallback(this)
        focusable = View.FOCUSABLE
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initializeGameDrawer()
        isRunning = true
        gameThread = Thread(this)
        gameThread.start()
    }

    private fun initializeGameDrawer() {
        drawBackground = DrawBackground(context, 2.0F)
        drawAircraft = Aircraft(context, 1.0F)
        enemies = Enemies(context, 1.0F)
        enemies.level = level
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
        val enemySize = ScreenUtils.dpToPx(context, 48.0f)

        // Check player aircraft colliding with enemies
        for (enemy in enemies.activeEnemies) {
            if (enemy.isDestroyed()) continue
            enemy.bitmap?.let {
                val enemyBounds = enemies.getEnemyBounds(enemy.x, enemy.y, it)
                if (RectF.intersects(aircraftBounds, enemyBounds)) {
                    if (!collisionCooldown) {
                        Log.d("Collision", "Aircraft collided with an enemy!")
                        handleCollision()
                    }
                } else {
                    collisionCooldown = false
                }
            }
        }

        // Check enemy bullets hitting the player
        checkEnemyBulletsHitPlayer(aircraftBounds)

        // Check player bullets hitting enemies
        checkPlayerBulletsHitEnemies()
    }

    private fun checkEnemyBulletsHitPlayer(aircraftBounds: RectF) {
        val enemyBullets = enemies.getEnemyBullets()
        for ((bx, by, bulletRef) in enemyBullets) {
            val bulletBounds = enemies.getBulletBounds(bx, by)
            if (RectF.intersects(aircraftBounds, bulletBounds)) {
                playerData.hit()
                musicService?.playerHitSoundPlay()
                Log.d("Game", "Player hit by enemy bullet! HP: ${playerData.health_points}")
                // Remove the bullet that hit
                for (enemy in enemies.activeEnemies) {
                    enemy.bullets.remove(bulletRef)
                }
                if (!playerData.isAlive()) {
                    musicService?.gameOverSoundPlay()
                    isRunning = false
                    Log.d("Game", "Game Over!")
                    post { onGameOver?.invoke() }
                }
                break
            }
        }
    }

    private fun checkPlayerBulletsHitEnemies() {
        val bullets = drawAircraft.getBullets()
        val enemySize = ScreenUtils.dpToPx(context, 48.0f)
        val bulletSize = ScreenUtils.dpToPx(context, 25.0f)

        for (bullet in bullets) {
            if (bullet.y < 0) continue
            val bulletBounds = RectF(
                bullet.x, bullet.y,
                bullet.x + bulletSize,
                bullet.y + bulletSize
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
                    musicService?.enemyHitSoundPlay()
                    Log.d("Game", "Enemy destroyed! Kills: $enemiesDestroyedThisLevel")
                    checkKillTarget()
                    break
                }
            }
        }
    }

    private fun handleCollision() {
        playerData.hit()
        collisionCooldown = true
        musicService?.playerHitSoundPlay()
        Log.d("Game", "Player hit! HP: ${playerData.health_points}")
        if (!playerData.isAlive()) {
            musicService?.gameOverSoundPlay()
            isRunning = false
            Log.d("Game", "Game Over!")
            post { onGameOver?.invoke() }
        }
    }

    private fun checkKillTarget() {
        if (enemiesDestroyedThisLevel < getRequiredKills(level)) return

        if (level >= MAX_LEVEL) {
            // Game won — all levels cleared
            gameWon = true
            isPaused = true
            Log.d("Game", "Game Won! All levels cleared!")
            post { onGameWon?.invoke() }
        } else {
            // Level complete — pause and notify
            isPaused = true
            Log.d("Game", "Level $level complete! Kills: $enemiesDestroyedThisLevel/${getRequiredKills(level)}")
            post { onLevelComplete?.invoke(level) }
        }
    }

    fun advanceToNextLevel() {
        level++
        enemies.level = level
        enemies.activeEnemies.clear()
        levelStartTimeMs = System.currentTimeMillis()
        enemiesDestroyedThisLevel = 0
        isPaused = false
        Log.d("Game", "Advanced to level $level")
    }

    private fun checkLevelTimer() {
        val elapsed = System.currentTimeMillis() - levelStartTimeMs
        if (elapsed < getLevelDurationMs(level)) return

        // Time expired without meeting kill target → game over
        musicService?.gameOverSoundPlay()
        isRunning = false
        Log.d("Game", "Level failed! Kills: $enemiesDestroyedThisLevel/${getRequiredKills(level)}")
        post { onGameOver?.invoke() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
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
        drawBackground(canvas)
        drawHeader(canvas)
        drawAircraft(canvas)
        if (!isPaused) {
            checkLevelTimer()
            drawEnemies(canvas)
            checkCollision()
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        enemies.onDraw(canvas)
    }

    private fun drawAircraft(canvas: Canvas) {
        drawAircraft.onDraw(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        drawBackground.onDraw(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        drawHeader.onDraw(canvas)
    }

    private var avg_FPS: Double = 0.0
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
            waitTime = targetTime - timeMillis

            try {
                Thread.sleep(abs(waitTime))
//                Thread.yield()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            totalTime += System.nanoTime() - startTime
            frameCount++
            if (frameCount == FPS) {
                avg_FPS = (1000 / ((totalTime / frameCount) / 1000000)).toDouble()
                frameCount = 0
                totalTime = 0
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("YoungTest", "$event ---- $keyCode")
        drawAircraft.updateGame()
        return super.onKeyDown(keyCode, event)
    }
}
