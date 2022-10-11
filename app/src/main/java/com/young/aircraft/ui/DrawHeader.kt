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
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.GREEN
        mPaint.style = Paint.Style.FILL_AND_STROKE;
        mPaint.textAlign = Paint.Align.LEFT
        mPaint.textSize = ScreenUtils.sp2px(context, 14.0f)
        canvas.drawText(context.getText(R.string.level) as String, 80F, 80F, mPaint)
    }

    override fun updateGame() {

    }
}