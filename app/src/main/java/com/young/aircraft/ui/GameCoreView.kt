package com.young.aircraft.ui

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
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
    private var surfaceHolder: SurfaceHolder? = null

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
        drawHeader = DrawHeader(context)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

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
        drawEnemies(canvas)
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