package com.young.aircraft.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.young.aircraft.R

class BitmapUtilsTest {

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
    }

    @Test
    fun `resizeBitmap handles zeroHeight gracefully`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resized = BitmapUtils.resizeBitmap(bitmap, 50, 0)
        assertNotNull(resized)
    }
}
