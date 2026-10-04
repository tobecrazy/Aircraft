package com.young.aircraft.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A short sequence of colorful bursts spread across the game canvas. */
internal class BossFireworksEffect(
    private val width: Float,
    private val height: Float,
    bossX: Float,
    bossY: Float,
    private val startedAt: Long = System.currentTimeMillis(),
    random: Random = Random(System.nanoTime())
) {
    private data class Particle(val angle: Float, val speed: Float, val radius: Float, val color: Int)
    private data class Burst(
        val x: Float,
        val y: Float,
        val delayMs: Long,
        val particles: List<Particle>,
        val radius: Float
    )

    private val colors = intArrayOf(
        Color.rgb(255, 78, 94),
        Color.rgb(255, 206, 74),
        Color.rgb(91, 220, 255),
        Color.rgb(190, 116, 255),
        Color.rgb(112, 255, 164)
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bursts: List<Burst>

    init {
        val positions = listOf(
            bossX to bossY,
            width * 0.14f to height * 0.28f,
            width * 0.34f to height * 0.48f,
            width * 0.68f to height * 0.30f,
            width * 0.87f to height * 0.50f,
            width * 0.50f to height * 0.22f
        )
        bursts = positions.mapIndexed { index, (x, y) ->
            Burst(
                x = x.coerceIn(0f, width),
                y = y.coerceIn(0f, height),
                delayMs = index * 430L,
                radius = minOf(width, height) * (0.16f + random.nextFloat() * 0.05f),
                particles = List(44) {
                    Particle(
                        angle = random.nextFloat() * (Math.PI * 2.0).toFloat(),
                        speed = 0.65f + random.nextFloat() * 0.55f,
                        radius = 5.5f + random.nextFloat() * 6.5f,
                        color = colors[random.nextInt(colors.size)]
                    )
                }
            )
        }
    }

    fun isFinished(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - startedAt >= DURATION_MS

    fun draw(canvas: Canvas, nowMs: Long = System.currentTimeMillis()) {
        val elapsed = nowMs - startedAt
        bursts.forEach { burst ->
            val ageMs = elapsed - burst.delayMs
            if (ageMs !in 0L..BURST_DURATION_MS) return@forEach

            val age = ageMs / 1000f
            val fade = (1f - ageMs / BURST_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            val expansion = 1f - age * 0.48f

            // A bright expanding ring makes the burst read clearly even behind the boss blast.
            if (ageMs < RING_DURATION_MS) {
                val ringProgress = ageMs / RING_DURATION_MS.toFloat()
                ringPaint.color = burst.particles.first().color
                ringPaint.alpha = ((1f - ringProgress) * 235).toInt()
                ringPaint.strokeWidth = (burst.radius * 0.035f * (1f - ringProgress * 0.55f))
                    .coerceAtLeast(3f)
                val ringRadius = burst.radius * (0.12f + ringProgress * 1.05f)
                canvas.drawCircle(burst.x, burst.y, ringRadius, ringPaint)
            }

            burst.particles.forEach { particle ->
                val distance = burst.radius * particle.speed * age * 1.9f
                val x = burst.x + cos(particle.angle) * distance
                val y = burst.y + sin(particle.angle) * distance + age * age * burst.radius * 0.65f
                val previousDistance = (burst.radius * particle.speed * (age - 0.055f).coerceAtLeast(0f) * 1.9f)
                val previousX = burst.x + cos(particle.angle) * previousDistance
                val previousY = burst.y + sin(particle.angle) * previousDistance +
                    (age - 0.055f).coerceAtLeast(0f).let { it * it * burst.radius * 0.65f }

                paint.color = particle.color
                paint.alpha = (fade * 255).toInt()
                paint.strokeWidth = particle.radius * 1.2f * expansion.coerceAtLeast(0.4f)
                canvas.drawLine(previousX, previousY, x, y, paint)
                canvas.drawCircle(x, y, particle.radius * expansion.coerceAtLeast(0.4f), paint)
            }

            // A brief central glint gives each burst a crisp firework pop.
            if (ageMs < GLINT_DURATION_MS) {
                val glintProgress = ageMs / GLINT_DURATION_MS.toFloat()
                paint.color = Color.WHITE
                paint.alpha = ((1f - glintProgress) * 255).toInt()
                canvas.drawCircle(burst.x, burst.y, burst.radius * 0.2f * (1f + glintProgress), paint)

                paint.alpha = ((1f - glintProgress) * 230).toInt()
                paint.strokeWidth = (burst.radius * 0.035f).coerceAtLeast(3f)
                val spoke = burst.radius * 0.42f * (1f + glintProgress)
                canvas.drawLine(burst.x - spoke, burst.y, burst.x + spoke, burst.y, paint)
                canvas.drawLine(burst.x, burst.y - spoke, burst.x, burst.y + spoke, paint)
            }
        }
    }

    private companion object {
        const val RING_DURATION_MS = 520L
        const val GLINT_DURATION_MS = 240L
        const val BURST_DURATION_MS = 1_250L
        const val DURATION_MS = 3_500L
    }
}
