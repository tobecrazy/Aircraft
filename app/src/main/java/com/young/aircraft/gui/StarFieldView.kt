package com.young.aircraft.gui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Animated star field background for cinematic screens.
 *
 * STAR LIFECYCLE: init (random positions) → draw loop (drift + twinkle)
 *   → pause (3s idle) → resume (touch via Activity dispatchTouchEvent)
 *
 * Two-layer parallax: background stars (slow, small, dim) + foreground stars (faster, larger, bright).
 * Pre-allocated arrays — zero per-frame allocation.
 * Auto-pauses after [IDLE_TIMEOUT_MS] of no user activity to save battery.
 */
class StarFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val STAR_COUNT = 65
        private const val FRAME_DELAY_MS = 50L // ~20 FPS
        private const val IDLE_TIMEOUT_MS = 3000L
        private const val BACKGROUND_COLOR = 0xFF0F1118.toInt()

        // Star colors: white, light blue, gold
        private val STAR_COLORS = intArrayOf(
            Color.WHITE,
            0xFFa8d8ea.toInt(),
            0xFFffd700.toInt()
        )
    }

    // Pre-allocated star data arrays (no per-frame allocation)
    private val starX = FloatArray(STAR_COUNT)
    private val starY = FloatArray(STAR_COUNT)
    private val starRadius = FloatArray(STAR_COUNT)
    private val starSpeed = FloatArray(STAR_COUNT)
    private val starAlpha = FloatArray(STAR_COUNT)
    private val starColorIndex = IntArray(STAR_COUNT)
    private val starIsBackground = BooleanArray(STAR_COUNT) // true = slow layer

    // Pre-allocated Paint objects
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isAnimating = false
    private var lastTouchTime = System.currentTimeMillis()
    private var isPausedByIdle = false

    private val random = Random(System.nanoTime())

    init {
        setBackgroundColor(BACKGROUND_COLOR)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initStars(w, h)
        }
    }

    private fun initStars(w: Int, h: Int) {
        val density = resources.displayMetrics.density
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat() * w
            starY[i] = random.nextFloat() * h
            starIsBackground[i] = i < STAR_COUNT * 2 / 3 // 2/3 background, 1/3 foreground
            if (starIsBackground[i]) {
                starRadius[i] = (0.5f + random.nextFloat() * 1.0f) * density
                starSpeed[i] = (0.5f + random.nextFloat() * 1.5f) * density
                starAlpha[i] = 0.3f + random.nextFloat() * 0.4f
            } else {
                starRadius[i] = (1.0f + random.nextFloat() * 1.5f) * density
                starSpeed[i] = (1.5f + random.nextFloat() * 2.5f) * density
                starAlpha[i] = 0.6f + random.nextFloat() * 0.4f
            }
            starColorIndex[i] = random.nextInt(STAR_COLORS.size)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (i in 0 until STAR_COUNT) {
            // Twinkle: oscillate alpha slightly
            val twinkle = 0.85f + 0.15f * kotlin.math.sin(
                (System.currentTimeMillis() * 0.003 + i * 0.7).toFloat()
            )
            val alpha = (starAlpha[i] * twinkle * 255).toInt().coerceIn(0, 255)

            starPaint.color = STAR_COLORS[starColorIndex[i]]
            starPaint.alpha = alpha
            canvas.drawCircle(starX[i], starY[i], starRadius[i], starPaint)
        }

        if (isAnimating && !isPausedByIdle) {
            updateStarPositions(w, h)
            postInvalidateDelayed(FRAME_DELAY_MS)
        }
    }

    private fun updateStarPositions(w: Float, h: Float) {
        // Check idle timeout
        if (System.currentTimeMillis() - lastTouchTime > IDLE_TIMEOUT_MS) {
            isPausedByIdle = true
            return
        }

        for (i in 0 until STAR_COUNT) {
            starY[i] += starSpeed[i]
            if (starY[i] > h + starRadius[i]) {
                starY[i] = -starRadius[i]
                starX[i] = random.nextFloat() * w
            }
        }
    }

    /** Called from Activity's dispatchTouchEvent to reset idle timer. */
    fun onUserActivity() {
        lastTouchTime = System.currentTimeMillis()
        if (isPausedByIdle) {
            isPausedByIdle = false
            if (isAnimating) {
                postInvalidateDelayed(FRAME_DELAY_MS)
            }
        }
    }

    fun startAnimation() {
        isAnimating = true
        lastTouchTime = System.currentTimeMillis()
        isPausedByIdle = false
        postInvalidateDelayed(FRAME_DELAY_MS)
    }

    fun stopAnimation() {
        isAnimating = false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAnimating) {
            lastTouchTime = System.currentTimeMillis()
            isPausedByIdle = false
            postInvalidateDelayed(FRAME_DELAY_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
