package com.young.aircraft.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ExplosionParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var radius: Float,
    var alpha: Int,
    val color: Int,
    var life: Float,
    val decay: Float
)

private data class SmokePuff(
    var x: Float,
    var y: Float,
    val vy: Float,
    var radius: Float,
    var alpha: Int
)

class ExplosionEffect(
    private val centerX: Float,
    private val centerY: Float,
    private val size: Float,
    scale: Float = 1f
) {
    private val startTime = System.currentTimeMillis()
    private val durationMs = (1000L * scale).toLong()
    private val particleCount = (20 * scale).toInt()
    private val smokeCount = (5 * scale).toInt()

    private val particles: List<ExplosionParticle>
    private val smokePuffs: List<SmokePuff>

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fireballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(size * 0.3f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        val rng = Random(System.nanoTime())
        val particleColors = intArrayOf(
            Color.rgb(255, 200, 50),   // bright yellow
            Color.rgb(255, 140, 20),   // orange
            Color.rgb(255, 80, 10),    // red-orange
            Color.rgb(255, 50, 0),     // red
            Color.rgb(200, 200, 200)   // light gray debris
        )

        particles = List(particleCount) {
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val speed = size * (0.8f + rng.nextFloat() * 1.5f) / (durationMs * 0.7f) * 33f
            ExplosionParticle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radius = size * (0.04f + rng.nextFloat() * 0.06f),
                alpha = 255,
                color = particleColors[rng.nextInt(particleColors.size)],
                life = 1f,
                decay = 0.015f + rng.nextFloat() * 0.01f
            )
        }

        smokePuffs = List(smokeCount) {
            SmokePuff(
                x = centerX + (rng.nextFloat() - 0.5f) * size * 0.5f,
                y = centerY + (rng.nextFloat() - 0.5f) * size * 0.3f,
                vy = -(size * 0.3f + rng.nextFloat() * size * 0.2f) / (durationMs * 0.5f) * 33f,
                radius = size * (0.15f + rng.nextFloat() * 0.1f),
                alpha = 180
            )
        }
    }

    fun isFinished(): Boolean =
        System.currentTimeMillis() - startTime >= durationMs

    fun draw(canvas: Canvas) {
        val elapsed = System.currentTimeMillis() - startTime
        val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)

        // Save layer for blur effects
        val saveCount = canvas.saveLayer(
            centerX - size * 2, centerY - size * 2,
            centerX + size * 2, centerY + size * 2,
            null
        )

        if (progress < 0.1f) drawFlash(canvas, progress)
        if (progress in 0.07f..0.4f) drawFireball(canvas, progress)
        if (progress in 0.13f..0.83f) drawParticles(canvas, progress)
        if (progress >= 0.5f) drawSmoke(canvas, progress)

        canvas.restoreToCount(saveCount)
    }

    private fun drawFlash(canvas: Canvas, progress: Float) {
        // Flash: 0.0 - 0.1 (rapid expand + fade)
        val phaseProgress = progress / 0.1f
        val radius = size * (0.3f + phaseProgress * 0.7f)
        val alpha = ((1f - phaseProgress) * 255).toInt().coerceIn(0, 255)

        flashPaint.color = Color.WHITE
        flashPaint.alpha = alpha
        flashPaint.maskFilter = BlurMaskFilter(radius * 0.5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(centerX, centerY, radius, flashPaint)

        // Inner yellow core
        flashPaint.color = Color.rgb(255, 240, 100)
        flashPaint.alpha = alpha
        flashPaint.maskFilter = null
        canvas.drawCircle(centerX, centerY, radius * 0.5f, flashPaint)
    }

    private fun drawFireball(canvas: Canvas, progress: Float) {
        // Fireball: 0.07 - 0.4 (expand then shrink)
        val phaseProgress = (progress - 0.07f) / 0.33f
        val expandProgress = if (phaseProgress < 0.4f) phaseProgress / 0.4f else 1f
        val shrinkProgress = if (phaseProgress >= 0.4f) (phaseProgress - 0.4f) / 0.6f else 0f
        val radius = size * (0.4f + expandProgress * 0.6f) * (1f - shrinkProgress * 0.7f)
        val alpha = ((1f - shrinkProgress) * 220).toInt().coerceIn(0, 255)

        val gradient = RadialGradient(
            centerX, centerY, radius.coerceAtLeast(1f),
            intArrayOf(
                Color.argb(alpha, 255, 230, 80),
                Color.argb(alpha, 255, 140, 20),
                Color.argb((alpha * 0.6f).toInt(), 200, 40, 0),
                Color.argb(0, 100, 20, 0)
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        fireballPaint.shader = gradient
        canvas.drawCircle(centerX, centerY, radius, fireballPaint)
        fireballPaint.shader = null
    }

    private fun drawParticles(canvas: Canvas, progress: Float) {
        // Particles: 0.13 - 0.83
        val phaseProgress = (progress - 0.13f) / 0.7f
        val gravity = size * 0.02f

        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            p.life -= p.decay
            // Apply gravity
            // (vy is modified indirectly through y accumulation)
            p.y += gravity * phaseProgress

            if (p.life <= 0f) continue

            val fadeAlpha = (p.life * 255 * (1f - phaseProgress * 0.5f)).toInt().coerceIn(0, 255)
            val currentRadius = p.radius * (0.3f + p.life * 0.7f)

            particlePaint.color = p.color
            particlePaint.alpha = fadeAlpha
            canvas.drawCircle(p.x, p.y, currentRadius.coerceAtLeast(1f), particlePaint)
        }
    }

    private fun drawSmoke(canvas: Canvas, progress: Float) {
        // Smoke: 0.5 - 1.0
        val phaseProgress = (progress - 0.5f) / 0.5f

        for (puff in smokePuffs) {
            puff.y += puff.vy
            puff.radius += 0.3f
            val fadeAlpha = (puff.alpha * (1f - phaseProgress)).toInt().coerceIn(0, 255)

            smokePaint.color = Color.rgb(100, 100, 100)
            smokePaint.alpha = fadeAlpha
            canvas.drawCircle(puff.x, puff.y, puff.radius, smokePaint)
        }
    }
}
