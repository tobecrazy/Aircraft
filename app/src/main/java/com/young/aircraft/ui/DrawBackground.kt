package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class DrawBackground(var context: Context, var speed: Float) : DrawBaseObject(context) {

    companion object {
        val BACKGROUNDS = intArrayOf(R.drawable.background, R.drawable.background_1 , R.drawable.background_2)
    }

    //background Top/Bottom
    var mTopY: Float = 0F
    var mBottomY: Float = 0F
    private var backgroundResId: Int = BACKGROUNDS.random()
    private var cachedBitmap: Bitmap? = null

    init {
        mTopY = -ScreenUtils.getScreenHeight(context).toFloat()
        loadBitmap()
    }

    private fun loadBitmap() {
        val originalBitmap = BitmapUtils.readBitMap(context, backgroundResId)
        val width = ScreenUtils.getScreenWidth(context)
        val height = ScreenUtils.getScreenHeight(context)
        cachedBitmap = BitmapUtils.resizeBitmap(originalBitmap, width, height)
    }

    fun randomizeBackground() {
        backgroundResId = BACKGROUNDS.random()
        cachedBitmap = null
        loadBitmap()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val bitmap = cachedBitmap ?: return
        canvas.density = bitmap.density
        mTopY += 10F * speed
        mBottomY += 10F * speed
        val height = ScreenUtils.getScreenHeight(context)
        if (mTopY > height || mBottomY > height) {
            mTopY = 0F
            mBottomY = -ScreenUtils.getScreenHeight(context).toFloat()
        }
        canvas.drawBitmap(bitmap, 0F, mTopY, mPaint)
        canvas.drawBitmap(bitmap, 0F, mBottomY, mPaint)
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}