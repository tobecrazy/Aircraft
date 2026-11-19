package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImagePreprocessorTest {

    @Test
    fun `gold and reflective surfaces are selected for enhancement`() {
        val gold = solidBitmap(Color.rgb(220, 180, 80))
        val glare = solidBitmap(Color.WHITE)

        assertTrue(ImagePreprocessor.needsBankCardEnhancement(gold))
        assertTrue(ImagePreprocessor.needsBankCardEnhancement(glare))

        val enhanced = ImagePreprocessor.prepareBankCardForOcr(gold, maxLongSide = 64)
        assertNotSame(gold, enhanced)
        assertEquals(64, enhanced.width)
        assertEquals(64, enhanced.height)
        val pixel = enhanced.getPixel(32, 32)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))

        gold.recycle()
        glare.recycle()
        enhanced.recycle()
    }

    @Test
    fun `metallic highlights on a blue card are selected for enhancement`() {
        val blueCard = solidBitmap(Color.rgb(90, 165, 205))
        for (y in 42..47) {
            for (x in 8..87) blueCard.setPixel(x, y, Color.rgb(245, 238, 220))
        }

        assertTrue(ImagePreprocessor.needsBankCardEnhancement(blueCard))

        blueCard.recycle()
    }

    @Test
    fun `gold chip on a blue card does not trigger whole image enhancement`() {
        val blueCard = solidBitmap(Color.rgb(90, 165, 205))
        for (y in 40..49) for (x in 40..54) blueCard.setPixel(x, y, Color.rgb(220, 180, 80))

        assertFalse(ImagePreprocessor.needsBankCardEnhancement(blueCard))

        blueCard.recycle()
    }

    @Test
    fun `neutral matte surfaces skip contrast enhancement`() {
        val matte = solidBitmap(Color.rgb(100, 100, 100))

        assertFalse(ImagePreprocessor.needsBankCardEnhancement(matte))
        assertSame(matte, ImagePreprocessor.prepareBankCardForOcr(matte, maxLongSide = 96))

        matte.recycle()
    }

    @Test
    fun `number band binary pass isolates local embossed edges`() {
        val source = solidBitmap(Color.rgb(100, 100, 100))
        for (y in 30..70) for (x in 40..43) source.setPixel(x, y, Color.WHITE)

        val binary = ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(source, maxLongSide = 96)

        assertEquals(96, binary.width)
        assertEquals(40, binary.height)
        assertEquals(Color.BLACK, binary.getPixel(41, 20))
        assertEquals(Color.WHITE, binary.getPixel(10, 20))
        source.recycle()
        binary.recycle()
    }

    private fun solidBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
