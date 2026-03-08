package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Create by Young
 **/
abstract class DrawBaseObject(context: Context) {
    protected var mPaint: Paint = Paint()


    init {
        mPaint.color = Color.WHITE
        mPaint.isAntiAlias = true
    }

    abstract fun onDraw(canvas: Canvas)
    abstract fun updateGame()
    abstract fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF
}