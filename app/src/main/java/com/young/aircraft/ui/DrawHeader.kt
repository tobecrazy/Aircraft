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

        // Draw timer countdown (center-top)
        val elapsed = System.currentTimeMillis() - gameView.levelStartTimeMs
        val remainingSec = ((GameCoreView.getLevelDurationMs(gameView.level) - elapsed) / 1000).coerceAtLeast(0)
        mPaint.color = if (remainingSec <= 10) Color.RED else Color.YELLOW
        mPaint.textAlign = Paint.Align.CENTER
        val centerX = ScreenUtils.getScreenWidth(context).toFloat() / 2
        canvas.drawText(context.getString(R.string.time_remaining, remainingSec.toInt()), centerX, floatY, mPaint)

        // Draw kill count (below level text)
        val requiredKills = GameCoreView.getRequiredKills(gameView.level)
        val killsY = floatY + ScreenUtils.dpToPx(context, 22.0F)
        mPaint.color = if (gameView.enemiesDestroyedThisLevel >= requiredKills) Color.GREEN else Color.WHITE
        mPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.kills_count, gameView.enemiesDestroyedThisLevel, requiredKills), floatX, killsY, mPaint)
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}
