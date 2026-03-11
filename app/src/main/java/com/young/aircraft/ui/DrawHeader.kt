package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.ScreenUtils
import com.young.aircraft.data.Aircraft as AircraftData

/**
 * Create by Young
 **/
class DrawHeader(
    var context: Context,
    private val playerData: AircraftData,
    private val gameView: GameCoreView
) : DrawBaseObject(context) {
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.GREEN
        mPaint.style = Paint.Style.FILL_AND_STROKE
        mPaint.textAlign = Paint.Align.LEFT
        mPaint.textSize = ScreenUtils.sp2px(context, 16.0f)
        val floatX = ScreenUtils.dpToPx(context, 40.0F).toFloat()
        val floatY = ScreenUtils.dpToPx(context, 35.0F).toFloat()
        canvas.drawText(
            context.getString(R.string.level, gameView.level.toString()),
            floatX,
            floatY,
            mPaint
        )

        // Draw HP
        val hpText = "HP: ${playerData.health_points.toInt()}"
        val hpX =
            ScreenUtils.getScreenWidth(context).toFloat() - ScreenUtils.dpToPx(context, 100.0F)
        if (playerData.health_points <= 30) {
            mPaint.color = Color.RED
        } else {
            mPaint.color = Color.GREEN
        }
        canvas.drawText(hpText, hpX, floatY, mPaint)

        // Draw HP bar below HP text
        val barWidth = ScreenUtils.dpToPx(context, 80.0f).toFloat()
        val barHeight = ScreenUtils.dpToPx(context, 8.0f).toFloat()
        val barY = floatY + ScreenUtils.dpToPx(context, 6.0f).toFloat()
        val barCorner = ScreenUtils.dpToPx(context, 3.0f).toFloat()
        val hpFraction = (playerData.health_points / 100f).coerceIn(0f, 1f)

        // Background
        mPaint.color = Color.DKGRAY
        mPaint.style = Paint.Style.FILL
        mPaint.textAlign = Paint.Align.LEFT
        canvas.drawRoundRect(
            RectF(hpX, barY, hpX + barWidth, barY + barHeight),
            barCorner,
            barCorner,
            mPaint
        )

        // Colored fill
        mPaint.color = when {
            hpFraction > 0.5f -> Color.GREEN
            hpFraction > 0.2f -> Color.YELLOW
            else -> Color.RED
        }
        val fillWidth = barWidth * hpFraction
        canvas.drawRoundRect(
            RectF(hpX, barY, hpX + fillWidth, barY + barHeight),
            barCorner,
            barCorner,
            mPaint
        )

        // White stroke border
        mPaint.color = Color.WHITE
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = 1f
        canvas.drawRoundRect(
            RectF(hpX, barY, hpX + barWidth, barY + barHeight),
            barCorner,
            barCorner,
            mPaint
        )
        mPaint.style = Paint.Style.FILL_AND_STROKE
        val elapsed = System.currentTimeMillis() - gameView.levelStartTimeMs
        val remainingSec =
            ((GameCoreView.getLevelDurationMs(gameView.level) - elapsed) / 1000).coerceAtLeast(0)
        mPaint.color = if (remainingSec <= 10) Color.RED else Color.YELLOW
        mPaint.textAlign = Paint.Align.CENTER
        val centerX = ScreenUtils.getScreenWidth(context).toFloat() / 2
        canvas.drawText(
            context.getString(R.string.time_remaining, remainingSec.toInt()),
            centerX,
            floatY,
            mPaint
        )

        // Draw kill count (below level text)
        val requiredKills = GameCoreView.getRequiredKills(gameView.level)
        val killsY = floatY + ScreenUtils.dpToPx(context, 22.0F)
        mPaint.color =
            if (gameView.enemiesDestroyedThisLevel >= requiredKills) Color.GREEN else Color.WHITE
        mPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(
                R.string.kills_count,
                gameView.enemiesDestroyedThisLevel,
                requiredKills
            ), floatX, killsY, mPaint
        )

        // Draw boss health bar below kills (only while boss is alive)
        val boss = gameView.bossEnemy.activeBoss
        if (boss != null && !boss.isDestroyed()) {
            val bossBarY = killsY + ScreenUtils.dpToPx(context, 10.0f).toFloat()
            val bossBarWidth = ScreenUtils.dpToPx(context, 120.0f).toFloat()
            val bossBarHeight = ScreenUtils.dpToPx(context, 10.0f).toFloat()
            val bossBarCorner = ScreenUtils.dpToPx(context, 3.0f).toFloat()
            val bossFraction = (boss.hitPoints / boss.maxHitPoints).coerceIn(0f, 1f)

            // Label
            mPaint.color = Color.RED
            mPaint.textSize = ScreenUtils.sp2px(context, 13.0f)
            mPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("BOSS", floatX, bossBarY + bossBarHeight, mPaint)

            val barLeft = floatX + ScreenUtils.dpToPx(context, 42.0f).toFloat()

            // Background
            mPaint.color = Color.DKGRAY
            mPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                RectF(barLeft, bossBarY, barLeft + bossBarWidth, bossBarY + bossBarHeight),
                bossBarCorner, bossBarCorner, mPaint
            )

            // Colored fill
            mPaint.color = when {
                bossFraction > 0.5f -> Color.RED
                bossFraction > 0.2f -> Color.YELLOW
                else -> Color.GREEN
            }
            val bossFillWidth = bossBarWidth * bossFraction
            canvas.drawRoundRect(
                RectF(barLeft, bossBarY, barLeft + bossFillWidth, bossBarY + bossBarHeight),
                bossBarCorner, bossBarCorner, mPaint
            )

            // White stroke border
            mPaint.color = Color.WHITE
            mPaint.style = Paint.Style.STROKE
            mPaint.strokeWidth = 1f
            canvas.drawRoundRect(
                RectF(barLeft, bossBarY, barLeft + bossBarWidth, bossBarY + bossBarHeight),
                bossBarCorner, bossBarCorner, mPaint
            )
            mPaint.style = Paint.Style.FILL_AND_STROKE
        }
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}
