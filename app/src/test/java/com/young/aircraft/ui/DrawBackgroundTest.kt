package com.young.aircraft.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import com.young.aircraft.utils.ScreenUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawBackgroundTest {

    @Test
    fun `background tiles remain contiguous while scrolling`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val width = ScreenUtils.getScreenWidth(activity)
        val height = ScreenUtils.getScreenHeight(activity)
        val canvas = Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        val drawBackground = DrawBackground(activity, speed = 1f)

        drawBackground.onDraw(canvas)
        val tiles = drawBackground.calculateVisibleTiles(canvas.height.toFloat())

        assertFalse(tiles.isEmpty())
        assertTrue(tiles.first().top <= 0f)
        assertTrue(tiles.last().bottom >= canvas.height.toFloat())

        for (index in 0 until tiles.lastIndex) {
            assertEquals(tiles[index].bottom, tiles[index + 1].top, 0.001f)
            assertTrue(tiles[index].mirrored != tiles[index + 1].mirrored)
        }
    }

    @Test
    fun `randomizeBackground preserves seamless tile coverage`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val width = ScreenUtils.getScreenWidth(activity)
        val height = ScreenUtils.getScreenHeight(activity)
        val canvas = Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        val drawBackground = DrawBackground(activity, speed = 1.5f)

        drawBackground.randomizeBackground()
        drawBackground.onDraw(canvas)
        val tiles = drawBackground.calculateVisibleTiles(canvas.height.toFloat())

        assertFalse(tiles.isEmpty())
        assertTrue(tiles.first().top <= 0f)
        assertTrue(tiles.last().bottom >= canvas.height.toFloat())
    }
}
