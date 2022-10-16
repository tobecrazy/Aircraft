package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.young.aircraft.R
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class DrawHeader(var context: Context) : DrawBaseObject(context) {
    @SuppressLint("DrawAllocation", "StringFormatInvalid")
    override fun onDraw(canvas: Canvas) {
        val mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.GREEN
        mPaint.style = Paint.Style.FILL_AND_STROKE;
        mPaint.textAlign = Paint.Align.LEFT
        mPaint.textSize = ScreenUtils.sp2px(context, 16.0f)
        val floatX = ScreenUtils.dpToPx(context, 40.0F).toFloat()
        val floatY = ScreenUtils.dpToPx(context, 35.0F).toFloat()
        canvas.drawText(context.getString(R.string.level, "一") as String, floatX, floatY, mPaint)
    }

    override fun updateGame() {

    }
}