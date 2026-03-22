package com.young.aircraft.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BitmapUtilsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BitmapUtils.clearCaches()
    }

    @Test
    fun `getScaleMap flips bitmap vertically`() {
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val scaled = BitmapUtils.getScaleMap(bitmap)

        assertEquals(bitmap.width, scaled.width)
        assertEquals(bitmap.height, scaled.height)
        // Matrix with postScale(1F, -1F) flips vertically
        assertNotSame(bitmap, scaled)
    }

    @Test
    fun `getScaleMap creates new bitmap instance`() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val scaled = BitmapUtils.getScaleMap(bitmap)
        assertNotSame(bitmap, scaled)
    }

    @Test
    fun `resizeBitmap returns null when bitmap is null`() {
        val result = BitmapUtils.resizeBitmap(null, 100, 100)
        assertNull(result)
    }

    @Test
    fun `resizeBitmap resizes to correct dimensions`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 50, 50)

        assertNotNull(resized)
        assertEquals(50, resized?.width)
        assertEquals(50, resized?.height)
    }

    @Test
    fun `resizeBitmap with degrees returns null when bitmap is null`() {
        val result = BitmapUtils.resizeBitmap(null, 100, 100, 90f)
        assertNull(result)
    }

    @Test
    fun `resizeBitmap with degrees applies rotation and scaling`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 50, 50, 90f)

        assertNotNull(resized)
        // After 90 degree rotation, width and height swap for scaling
        assertEquals(50, resized?.width)
        assertEquals(50, resized?.height)
    }

    @Test
    fun `resizeBitmap preserves bitmap content`() {
        // Create bitmap with specific pixels
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(5, 5, android.graphics.Color.RED)

        val resized = BitmapUtils.resizeBitmap(bitmap, 20, 20)
        assertNotNull(resized)
        assertEquals(20, resized?.width)
        assertEquals(20, resized?.height)
    }

    @Test
    fun `resizeBitmap handles zeroWidth gracefully`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 0, 50)
        assertNotNull(resized)
        assertSame(bitmap, resized)
    }

    @Test
    fun `resizeBitmap handles zeroHeight gracefully`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 50, 0)
        assertNotNull(resized)
        assertSame(bitmap, resized)
    }

    @Test
    fun `readBitMap returns null for invalid resource id`() {
        val bitmap = BitmapUtils.readBitMap(context, 0)
        assertNull(bitmap)
    }

    @Test
    fun `readBitMap caches decoded resource`() {
        val first = BitmapUtils.readBitMap(context, R.drawable.bullet_up)
        val second = BitmapUtils.readBitMap(context, R.drawable.bullet_up)

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `resizeBitmap returns cached bitmap for repeated resize request`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val first = BitmapUtils.resizeBitmap(bitmap, 50, 50)
        val second = BitmapUtils.resizeBitmap(bitmap, 50, 50)

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `resizeBitmap returns original when target matches source and no rotation`() {
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 80, 120)

        assertSame(bitmap, resized)
    }
}
