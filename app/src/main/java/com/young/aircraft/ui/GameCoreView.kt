package com.young.aircraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils


/**
 * Create by Young
 **/
class GameCoreView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private lateinit var gameThread: Thread
    private var surfaceHolder: SurfaceHolder? = null

    init {
        surfaceHolder = holder
        surfaceHolder?.addCallback(this)
        focusable = View.FOCUSABLE
        isFocusableInTouchMode = true
        keepScreenOn = true

    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        gameThread = Thread(this)
        gameThread.start()
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
    }

    private fun drawAircraft(canvas: Canvas) {
        val bitmap = BitmapUtils.readBitMap(context, R.drawable.jet_plane)
        if (bitmap != null) {
            bitmap.density = resources.displayMetrics.densityDpi
            val rectF: RectF = RectF(
                /* left = */ 0F,
                /* top = */ 0F,
                /* right = */ ScreenUtils.getScreenWidth(context).toFloat() / 10,
                /* bottom = */ ScreenUtils.getScreenHeight(context).toFloat() / 10
            )
            bitmap.density = resources.displayMetrics.densityDpi
            canvas.drawBitmap(bitmap, null, rectF, null)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val drawBackground = DrawBackground(context)
        drawBackground.onDraw(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        val drawHeader = DrawHeader(context)
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
                        println("===> unlockCanvasAndPost")
                        surfaceHolder?.unlockCanvasAndPost(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            timeMillis = (System.nanoTime() - startTime) / 1000000
            waitTime = targetTime - timeMillis

            try {
                println("waitTime ===> $waitTime")
                Thread.sleep(waitTime)
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
}