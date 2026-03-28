package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.ScreenUtils
import com.young.aircraft.data.PlayerAircraft as AircraftData

/**
 * Create by Young
 **/
class DrawHeader(
    var context: Context,
    private val playerData: AircraftData,
    private val gameView: GameCoreView
) : DrawBaseObject(context) {
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL_AND_STROKE
        textAlign = Paint.Align.LEFT
        textSize = ScreenUtils.sp2px(context, 16.0f)
    }
    private val levelX = ScreenUtils.dpToPx(context, 40.0f).toFloat()
    private val headerY = ScreenUtils.dpToPx(context, 35.0f).toFloat()
    private val hpX =
        ScreenUtils.getScreenWidth(context).toFloat() - ScreenUtils.dpToPx(context, 100.0f)
    private val hpBarWidth = ScreenUtils.dpToPx(context, 80.0f).toFloat()
    private val hpBarHeight = ScreenUtils.dpToPx(context, 8.0f).toFloat()
    private val hpBarY = headerY + ScreenUtils.dpToPx(context, 6.0f).toFloat()
    private val hpBarCorner = ScreenUtils.dpToPx(context, 3.0f).toFloat()
    private val killsY = headerY + ScreenUtils.dpToPx(context, 22.0F)
    private val bossBarWidth = ScreenUtils.dpToPx(context, 120.0f).toFloat()
    private val bossBarHeight = ScreenUtils.dpToPx(context, 10.0f).toFloat()
    private val bossBarCorner = ScreenUtils.dpToPx(context, 3.0f).toFloat()
    private val bossBarLeft = levelX + ScreenUtils.dpToPx(context, 42.0f).toFloat()
    private val hpBarRect = RectF()
    private val hpFillRect = RectF()
    private val bossBarRect = RectF()
    private val bossFillRect = RectF()

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(
            context.getString(R.string.level, gameView.level.toString()),
            levelX,
            headerY,
            hudPaint
        )

        // Draw HP
        val hpText = "HP: ${playerData.health_points.toInt()}"
        if (playerData.health_points <= 30) {
            hudPaint.color = Color.RED
        } else {
            hudPaint.color = Color.GREEN
        }
        canvas.drawText(hpText, hpX, headerY, hudPaint)

        // Draw HP bar below HP text
        val hpFraction = (playerData.health_points / 100f).coerceIn(0f, 1f)
        hpBarRect.set(hpX, hpBarY, hpX + hpBarWidth, hpBarY + hpBarHeight)
        hpFillRect.set(hpX, hpBarY, hpX + hpBarWidth * hpFraction, hpBarY + hpBarHeight)

        // Background
        hudPaint.color = Color.DKGRAY
        hudPaint.style = Paint.Style.FILL
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawRoundRect(hpBarRect, hpBarCorner, hpBarCorner, hudPaint)

        // Colored fill
        hudPaint.color = when {
            hpFraction > 0.5f -> Color.GREEN
            hpFraction > 0.2f -> Color.YELLOW
            else -> Color.RED
        }
        canvas.drawRoundRect(hpFillRect, hpBarCorner, hpBarCorner, hudPaint)

        // White stroke border
        hudPaint.color = Color.WHITE
        hudPaint.style = Paint.Style.STROKE
        hudPaint.strokeWidth = 1f
        canvas.drawRoundRect(hpBarRect, hpBarCorner, hpBarCorner, hudPaint)
        hudPaint.style = Paint.Style.FILL_AND_STROKE
        val elapsed = System.currentTimeMillis() - gameView.levelStartTimeMs
        val remainingSec =
            ((GameCoreView.getLevelDurationMs(gameView.level) - elapsed) / 1000).coerceAtLeast(0)
        hudPaint.color = if (remainingSec <= 10) Color.RED else Color.YELLOW
        hudPaint.textAlign = Paint.Align.CENTER
        val centerX = ScreenUtils.getScreenWidth(context).toFloat() / 2
        canvas.drawText(
            context.getString(R.string.time_remaining, remainingSec.toInt()),
            centerX,
            headerY,
            hudPaint
        )

        // Draw kill count (below level text)
        val requiredKills = GameCoreView.getRequiredKills(gameView.level)
        hudPaint.color =
            if (gameView.enemiesDestroyedThisLevel >= requiredKills) Color.GREEN else Color.WHITE
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(
                R.string.kills_count,
                gameView.enemiesDestroyedThisLevel,
                requiredKills
            ), levelX, killsY, hudPaint
        )

        // Draw boss health bar below kills (only while boss is alive)
        val boss = gameView.bossEnemy.activeBoss
        if (boss != null && !boss.isDestroyed()) {
            val bossBarY = killsY + ScreenUtils.dpToPx(context, 10.0f).toFloat()
            val bossFraction = (boss.hitPoints / boss.maxHitPoints).coerceIn(0f, 1f)
            bossBarRect.set(bossBarLeft, bossBarY, bossBarLeft + bossBarWidth, bossBarY + bossBarHeight)
            bossFillRect.set(
                bossBarLeft,
                bossBarY,
                bossBarLeft + bossBarWidth * bossFraction,
                bossBarY + bossBarHeight
            )

            // Label
            hudPaint.color = Color.RED
            hudPaint.textSize = ScreenUtils.sp2px(context, 13.0f)
            hudPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("BOSS", levelX, bossBarY + bossBarHeight, hudPaint)

            // Background
            hudPaint.color = Color.DKGRAY
            hudPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bossBarRect, bossBarCorner, bossBarCorner, hudPaint)

            // Colored fill
            hudPaint.color = when {
                bossFraction > 0.5f -> Color.RED
                bossFraction > 0.2f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawRoundRect(bossFillRect, bossBarCorner, bossBarCorner, hudPaint)

            // White stroke border
            hudPaint.color = Color.WHITE
            hudPaint.style = Paint.Style.STROKE
            hudPaint.strokeWidth = 1f
            canvas.drawRoundRect(bossBarRect, bossBarCorner, bossBarCorner, hudPaint)
            hudPaint.style = Paint.Style.FILL_AND_STROKE
            hudPaint.textSize = ScreenUtils.sp2px(context, 16.0f)
        }
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}
