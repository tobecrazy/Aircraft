package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.data.Aircraft as AircraftData
import com.young.aircraft.utils.ScreenUtils

/**
 * Create by Young
 **/
class DrawHeader(var context: Context, private val playerData: AircraftData, private val gameView: GameCoreView) : DrawBaseObject(context) {
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.GREEN
        mPaint.style = Paint.Style.FILL_AND_STROKE;
        mPaint.textAlign = Paint.Align.LEFT
        mPaint.textSize = ScreenUtils.sp2px(context, 16.0f)
        val floatX = ScreenUtils.dpToPx(context, 40.0F).toFloat()
        val floatY = ScreenUtils.dpToPx(context, 35.0F).toFloat()
        canvas.drawText(context.getString(R.string.level, gameView.level.toString()), floatX, floatY, mPaint)

        // Draw HP
        val hpText = "HP: ${playerData.health_points.toInt()}"
        val hpX = ScreenUtils.getScreenWidth(context).toFloat() - ScreenUtils.dpToPx(context, 100.0F)
        if (playerData.health_points <= 30) {
            mPaint.color = Color.RED
        } else {
            mPaint.color = Color.GREEN
        }
        canvas.drawText(hpText, hpX, floatY, mPaint)
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}