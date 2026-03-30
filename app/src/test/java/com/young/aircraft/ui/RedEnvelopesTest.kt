package com.young.aircraft.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.young.aircraft.data.RedEnvelopeState
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedEnvelopesTest {
    private lateinit var context: Context
    private lateinit var canvas: Canvas

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).setup().get()
        BitmapUtils.clearCaches()
        canvas = Canvas(
            Bitmap.createBitmap(
                ScreenUtils.getScreenWidth(context),
                ScreenUtils.getScreenHeight(context),
                Bitmap.Config.ARGB_8888
            )
        )
    }

    @Test
    fun `spawns envelope after full interval when screen is empty`() {
        val redEnvelopes = RedEnvelopes(context, speed = 0f)
        redEnvelopes.clearAll()

        repeat(RedEnvelopes.SPAWN_INTERVAL_FRAMES - 1) {
            redEnvelopes.onDraw(canvas)
        }

        assertTrue(redEnvelopes.activeEnvelopes.isEmpty())

        redEnvelopes.onDraw(canvas)

        assertEquals(1, redEnvelopes.activeEnvelopes.size)
    }

    @Test
    fun `spawn timer resets while an envelope is already on screen`() {
        val redEnvelopes = RedEnvelopes(context, speed = 0f)
        redEnvelopes.clearAll()
        redEnvelopes.activeEnvelopes.add(RedEnvelopeState(x = 40f, y = 40f))

        repeat(RedEnvelopes.SPAWN_INTERVAL_FRAMES + 20) {
            redEnvelopes.onDraw(canvas)
        }

        assertEquals(1, redEnvelopes.activeEnvelopes.size)

        redEnvelopes.activeEnvelopes.clear()
        redEnvelopes.onDraw(canvas)

        assertTrue(redEnvelopes.activeEnvelopes.isEmpty())

        repeat(RedEnvelopes.SPAWN_INTERVAL_FRAMES - 1) {
            redEnvelopes.onDraw(canvas)
        }

        assertEquals(1, redEnvelopes.activeEnvelopes.size)
    }
}
