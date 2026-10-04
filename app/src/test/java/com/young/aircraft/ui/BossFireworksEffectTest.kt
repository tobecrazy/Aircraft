package com.young.aircraft.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * [BossFireworksEffect] is internal render-thread drawing code with no callers under test, so the
 * only thing worth pinning is the frame window: nothing before the first burst, ink while bursts
 * are alive, and a hard stop at the declared duration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BossFireworksEffectTest {

    private val startedAt = 1_000_000L

    private fun effect(bossX: Float = 500f, bossY: Float = 300f) =
        BossFireworksEffect(
            width = 1000f,
            height = 1600f,
            bossX = bossX,
            bossY = bossY,
            startedAt = startedAt,
            random = Random(42)
        )

    private fun paintedPixelCount(fireworks: BossFireworksEffect, nowMs: Long): Int {
        val bitmap = Bitmap.createBitmap(1000, 1600, Bitmap.Config.ARGB_8888)
        fireworks.draw(Canvas(bitmap), nowMs)
        var painted = 0
        for (x in 0 until 1000 step 7) {
            for (y in 0 until 1600 step 7) {
                if (bitmap.getPixel(x, y) != Color.TRANSPARENT) painted++
            }
        }
        bitmap.recycle()
        return painted
    }

    @Test
    fun `not finished on the starting frame and finished past the duration`() {
        val fireworks = effect()

        assertFalse(fireworks.isFinished(startedAt))
        assertFalse(fireworks.isFinished(startedAt + 3_400L))
        assertTrue(fireworks.isFinished(startedAt + 3_500L))
        assertTrue(fireworks.isFinished(startedAt + 10_000L))
    }

    @Test
    fun `draws nothing before the first burst starts`() {
        assertTrue(paintedPixelCount(effect(), startedAt - 1) == 0)
    }

    @Test
    fun `paints during the burst window and stops once finished`() {
        val fireworks = effect()

        assertTrue(paintedPixelCount(fireworks, startedAt + 200L) > 0)
        assertTrue(paintedPixelCount(fireworks, startedAt + 2_400L) > 0)
        assertTrue(paintedPixelCount(fireworks, startedAt + 5_000L) == 0)
    }

    @Test
    fun `bursts stay inside the canvas even for an off-screen boss position`() {
        val fireworks = effect(bossX = -500f, bossY = 9_000f)

        // An out-of-range boss must not throw or produce NaN coordinates; clamping keeps it drawable.
        assertTrue(paintedPixelCount(fireworks, startedAt + 200L) > 0)
    }

    @Test
    fun `burst positions are clamped to the canvas bounds`() {
        // bossX/bossY are coerced in the constructor, so a boss at the origin still paints in-frame.
        assertTrue(paintedPixelCount(effect(bossX = -1f, bossY = -1f), startedAt + 200L) > 0)
    }
}
