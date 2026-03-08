package com.young.aircraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import android.view.KeyEvent
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
    var musicService: MusicService? = null
    var level: Int = 1
    private var lastEnemyY: Float = 0f
    private var wavesCleared: Int = 0

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
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    private fun checkCollision() {
        val aircraftBounds = drawAircraft.getBounds()

        // Check player aircraft colliding with enemies
        enemies.enemiesMap.forEach { (enemyX, enemyBitmap) ->
            enemyBitmap?.let {
                val enemyBounds = enemies.getEnemyBounds(enemyX, enemies.enemyY, it)
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
        for ((bx, by) in enemyBullets) {
            val bulletBounds = enemies.getBulletBounds(bx, by)
            if (RectF.intersects(aircraftBounds, bulletBounds)) {
                playerData.hit()
                musicService?.playerHitSoundPlay()
                Log.d("Game", "Player hit by enemy bullet! HP: ${playerData.health_points}")
                // Remove the bullet that hit
                for (enemy in enemies.enemyStates) {
                    enemy.bullets.remove(by)
                }
                if (!playerData.isAlive()) {
                    musicService?.gameOverSoundPlay()
                    isRunning = false
                    Log.d("Game", "Game Over!")
                }
                break
            }
        }
    }

    private fun checkPlayerBulletsHitEnemies() {
        val bulletX = drawAircraft.getBulletX()
        val bulletYPositions = drawAircraft.getBulletYPositions()
        val enemySize = ScreenUtils.dpToPx(context, 48.0f)

        for (bulletY in bulletYPositions) {
            if (bulletY < 0) continue
            val bulletBounds = RectF(
                bulletX, bulletY,
                bulletX + ScreenUtils.dpToPx(context, 25.0f),
                bulletY + ScreenUtils.dpToPx(context, 25.0f)
            )

            for (enemy in enemies.enemyStates) {
                if (enemy.isDestroyed()) continue
                val enemyBounds = RectF(
                    enemy.x, enemies.enemyY,
                    enemy.x + enemySize, enemies.enemyY + enemySize
                )
                if (RectF.intersects(bulletBounds, enemyBounds)) {
                    val hitEnemy = enemies.hitEnemy(enemy.x)
                    if (hitEnemy != null) {
                        if (hitEnemy.isDestroyed()) {
                            musicService?.enemyHitSoundPlay()
                            Log.d("Game", "Enemy destroyed!")
                        } else {
                            musicService?.enemyHitSoundPlay()
                            Log.d("Game", "Enemy hit! HP: ${hitEnemy.health}")
                        }
                    }
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
        }
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
        checkLevelUp()
        drawEnemies(canvas)
        checkCollision()
    }

    private fun checkLevelUp() {
        if (enemies.enemyY < lastEnemyY && lastEnemyY > 0) {
            wavesCleared++
            if (wavesCleared >= WAVES_PER_LEVEL) {
                wavesCleared = 0
                level++
                enemies.level = level
                Log.d("Game", "Level up! Now level $level")
            }
        }
        lastEnemyY = enemies.enemyY
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


    companion object {
        const val FPS: Int = 30
        const val WAVES_PER_LEVEL = 3
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("YoungTest", "$event ---- $keyCode")
        drawAircraft.updateGame()
        return super.onKeyDown(keyCode, event)
    }
}
