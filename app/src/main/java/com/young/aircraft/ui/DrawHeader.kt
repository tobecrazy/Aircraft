package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
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

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ScreenUtils.dpToPx(context, 1.0f).toFloat()
        color = Color.parseColor(AircraftConstants.HudColors.STROKE_GREEN)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(AircraftConstants.HudColors.LABEL_GREEN)
        textSize = ScreenUtils.sp2px(context, 11.0f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = ScreenUtils.sp2px(context, 16.0f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(AircraftConstants.HudColors.VALUE_LIGHT)
        textSize = ScreenUtils.sp2px(context, 12.0f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val emphasisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(AircraftConstants.HudColors.EMPHASIS_GREEN)
        textSize = ScreenUtils.sp2px(context, 18.0f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val panelCorner = ScreenUtils.dpToPx(context, 16.0f).toFloat()
    private val smallCorner = ScreenUtils.dpToPx(context, 8.0f).toFloat()

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val screenWidth = ScreenUtils.getScreenWidth(context).toFloat()
        val margin = ScreenUtils.dpToPx(context, 16.0f).toFloat()
        val top = ScreenUtils.dpToPx(context, 16.0f).toFloat()
        val leftCardWidth = ScreenUtils.dpToPx(context, 150.0f).toFloat()
        val rightCardWidth = ScreenUtils.dpToPx(context, 132.0f).toFloat()
        val cardHeight = ScreenUtils.dpToPx(context, 64.0f).toFloat()
        val timerWidth = ScreenUtils.dpToPx(context, 116.0f).toFloat()
        val timerHeight = ScreenUtils.dpToPx(context, 42.0f).toFloat()

        val leftCard = RectF(margin, top, margin + leftCardWidth, top + cardHeight)
        val rightCard = RectF(
            screenWidth - margin - rightCardWidth,
            top,
            screenWidth - margin,
            top + cardHeight
        )
        val timerTop = top + cardHeight + ScreenUtils.dpToPx(context, 8.0f)
        val timerCard = RectF(
            (screenWidth - timerWidth) / 2f,
            timerTop,
            (screenWidth + timerWidth) / 2f,
            timerTop + timerHeight
        )

        drawPanel(canvas, leftCard, AircraftConstants.HudColors.PANEL_DARK)
        drawPanel(canvas, timerCard, AircraftConstants.HudColors.PANEL_TIMER)
        drawPanel(canvas, rightCard, AircraftConstants.HudColors.PANEL_DARK)

        val requiredKills = GameCoreView.getRequiredKills(gameView.level)
        val remainingSec = GameHudFormatter.calculateRemainingSeconds(
            level = gameView.level,
            elapsedMs = System.currentTimeMillis() - gameView.levelStartTimeMs
        )
        val healthPercent = GameHudFormatter.formatHealthPercent(playerData.health_points)

        labelPaint.textAlign = Paint.Align.LEFT
        titlePaint.textAlign = Paint.Align.LEFT
        valuePaint.textAlign = Paint.Align.LEFT

        canvas.drawText(
            AircraftConstants.HudLabels.MISSION,
            leftCard.left + ScreenUtils.dpToPx(context, 12.0f),
            leftCard.top + ScreenUtils.dpToPx(context, 18.0f),
            labelPaint
        )
        canvas.drawText(
            context.getString(R.string.level, gameView.level.toString()),
            leftCard.left + ScreenUtils.dpToPx(context, 12.0f),
            leftCard.top + ScreenUtils.dpToPx(context, 38.0f),
            titlePaint
        )
        valuePaint.color =
            if (gameView.enemiesDestroyedThisLevel >= requiredKills) Color.parseColor(AircraftConstants.HudColors.EMPHASIS_GREEN)
            else Color.parseColor(AircraftConstants.HudColors.VALUE_LIGHT)
        canvas.drawText(
            context.getString(
                R.string.kills_count,
                gameView.enemiesDestroyedThisLevel,
                requiredKills
            ),
            leftCard.left + ScreenUtils.dpToPx(context, 12.0f),
            leftCard.top + ScreenUtils.dpToPx(context, 56.0f),
            valuePaint
        )

        labelPaint.textAlign = Paint.Align.CENTER
        emphasisPaint.color = when {
            remainingSec <= 10 -> Color.parseColor(AircraftConstants.HudColors.TIME_CRITICAL)
            remainingSec <= 30 -> Color.parseColor(AircraftConstants.HudColors.TIME_WARNING)
            else -> Color.parseColor(AircraftConstants.HudColors.TIME_NORMAL)
        }
        canvas.drawText(
            AircraftConstants.HudLabels.TIME,
            timerCard.centerX(),
            timerCard.top + ScreenUtils.dpToPx(context, 16.0f),
            labelPaint
        )
        canvas.drawText(
            context.getString(R.string.time_remaining, remainingSec),
            timerCard.centerX(),
            timerCard.top + ScreenUtils.dpToPx(context, 34.0f),
            emphasisPaint
        )

        labelPaint.textAlign = Paint.Align.LEFT
        titlePaint.textAlign = Paint.Align.LEFT
        valuePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            AircraftConstants.HudLabels.HULL,
            rightCard.left + ScreenUtils.dpToPx(context, 12.0f),
            rightCard.top + ScreenUtils.dpToPx(context, 18.0f),
            labelPaint
        )
        titlePaint.color = when {
            healthPercent <= 20 -> Color.parseColor(AircraftConstants.HudColors.HULL_LOW)
            healthPercent <= 50 -> Color.parseColor(AircraftConstants.HudColors.HULL_MID)
            else -> Color.WHITE
        }
        canvas.drawText(
            "$healthPercent%",
            rightCard.left + ScreenUtils.dpToPx(context, 12.0f),
            rightCard.top + ScreenUtils.dpToPx(context, 38.0f),
            titlePaint
        )
        valuePaint.color = Color.parseColor(AircraftConstants.HudColors.SCORE_VALUE)
        canvas.drawText(
            "${GameHudFormatter.calculateScore(gameView.totalKills)}",
            rightCard.right - ScreenUtils.dpToPx(context, 12.0f),
            rightCard.top + ScreenUtils.dpToPx(context, 38.0f),
            valuePaint
        )

        val healthBarBg = RectF(
            rightCard.left + ScreenUtils.dpToPx(context, 12.0f),
            rightCard.top + ScreenUtils.dpToPx(context, 46.0f),
            rightCard.right - ScreenUtils.dpToPx(context, 12.0f),
            rightCard.top + ScreenUtils.dpToPx(context, 54.0f)
        )
        drawProgressBar(canvas, healthBarBg, healthPercent / 100f, when {
            healthPercent <= 20 -> AircraftConstants.HudColors.HEALTH_LOW
            healthPercent <= 50 -> AircraftConstants.HudColors.HEALTH_MID
            else -> AircraftConstants.HudColors.HEALTH_HIGH
        })

        titlePaint.color = Color.WHITE

        val boss = gameView.bossEnemy.activeBoss
        if (boss != null && !boss.isDestroyed()) {
            val bossBarTop = timerCard.bottom + ScreenUtils.dpToPx(context, 8.0f)
            val bossCard = RectF(
                margin,
                bossBarTop,
                screenWidth - margin,
                bossBarTop + ScreenUtils.dpToPx(context, 28.0f)
            )
            drawPanel(canvas, bossCard, AircraftConstants.HudColors.PANEL_BOSS)
            labelPaint.color = Color.parseColor(AircraftConstants.HudColors.LABEL_GREEN)
            canvas.drawText(
                AircraftConstants.HudLabels.BOSS,
                bossCard.left + ScreenUtils.dpToPx(context, 12.0f),
                bossCard.top + ScreenUtils.dpToPx(context, 18.0f),
                labelPaint
            )
            labelPaint.color = Color.parseColor(AircraftConstants.HudColors.LABEL_GREEN)
            val bossBarRect = RectF(
                bossCard.left + ScreenUtils.dpToPx(context, 52.0f),
                bossCard.top + ScreenUtils.dpToPx(context, 10.0f),
                bossCard.right - ScreenUtils.dpToPx(context, 12.0f),
                bossCard.top + ScreenUtils.dpToPx(context, 18.0f)
            )
            drawProgressBar(
                canvas,
                bossBarRect,
                (boss.hitPoints / boss.maxHitPoints).coerceIn(0f, 1f),
                AircraftConstants.HudColors.BOSS_BAR
            )
        }
    }

    private fun drawPanel(canvas: Canvas, rect: RectF, fillColor: String) {
        panelPaint.style = Paint.Style.FILL
        panelPaint.color = Color.parseColor(fillColor)
        canvas.drawRoundRect(rect, panelCorner, panelCorner, panelPaint)
        canvas.drawRoundRect(rect, panelCorner, panelCorner, strokePaint)
    }

    private fun drawProgressBar(canvas: Canvas, rect: RectF, progress: Float, fillColor: String) {
        barPaint.color = Color.parseColor(AircraftConstants.HudColors.PROGRESS_BAR_BG)
        canvas.drawRoundRect(rect, smallCorner, smallCorner, barPaint)

        val progressRect = RectF(rect.left, rect.top, rect.left + rect.width() * progress, rect.bottom)
        barPaint.color = Color.parseColor(fillColor)
        canvas.drawRoundRect(progressRect, smallCorner, smallCorner, barPaint)
    }

    override fun updateGame() {
    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}
