package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Device-side check for the number-line variants.
 *
 * The Robolectric suite can only exercise the Kotlin fallback — the native
 * library never loads in a JVM test — so this asserts on real hardware that the
 * packaged arm64 OpenCV build initialises and that its filters separate an
 * embossed stroke from both a dark card face and a bright printed graphic. On
 * an ABI whose `.so` is not packaged it asserts the fallback instead.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvSupportTest {

    @Test
    fun numberLineVariantsSeparateStrokesOnDevice() {
        val card = embossedBlueCard()
        val flattened = ImagePreprocessor.prepareBankCardNumberLineForOcr(card, maxLongSide = 2048)
        val binary = checkNotNull(
            ImagePreprocessor.prepareBankCardNumberLineAdaptiveForOcr(card, maxLongSide = 2048)
        )

        // The tight crop is enlarged up to the cap and never past it.
        assertEquals(2048, flattened.width)
        assertEquals(2048, binary.width)

        val scale = flattened.width.toFloat() / card.width
        val lineTop = (card.height * LINE_TOP_RATIO).roundToInt()
        val strokeRow = (((STROKE_TOP + STROKE_BOTTOM) / 2f - lineTop) * scale).toInt()
        val onCardFace = ((STROKE_ON_FACE + 1.5f) * scale).toInt()
        val onGraphic = ((STROKE_ON_GRAPHIC + 1.5f) * scale).toInt()

        // The stroke over the blue face is brighter than the flattened mid-grey, the
        // one over the printed graphic darker; both must clear it by a wide margin.
        assertTrue(luminance(flattened.getPixel(onCardFace, strokeRow)) >= 128 + 40)
        assertTrue(luminance(flattened.getPixel(onGraphic, strokeRow)) <= 128 - 20)
        assertEquals(Color.BLACK, binary.getPixel(onCardFace, strokeRow))
        assertEquals(Color.BLACK, binary.getPixel(onGraphic, strokeRow))
        assertEquals(Color.WHITE, binary.getPixel((20 * scale).toInt(), strokeRow))

        if (Build.SUPPORTED_ABIS.first() == "arm64-v8a") {
            assertTrue(
                "packaged arm64 OpenCV library did not initialise",
                OpenCvSupport.isAvailable()
            )
        } else {
            // Only the arm64 build ships; every other ABI must fall back cleanly.
            assertFalse(OpenCvSupport.isAvailable())
        }

        card.recycle()
        flattened.recycle()
        binary.recycle()
    }

    /**
     * Blue card with a small gold chip, a printed light graphic over the right
     * third, and narrow embossed strokes crossing both.
     */
    private fun embossedBlueCard(): Bitmap {
        val card = Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888)
        card.eraseColor(CARD_FACE)
        for (y in 30..39) for (x in 340..354) card.setPixel(x, y, Color.rgb(220, 180, 80))
        for (y in 0 until 260) for (x in 260 until 400) card.setPixel(x, y, CARD_GRAPHIC)
        for (y in STROKE_TOP..STROKE_BOTTOM) for (x in STROKE_ON_FACE until STROKE_ON_FACE + 3) {
            card.setPixel(x, y, STROKE_OVER_FACE)
        }
        for (y in STROKE_TOP..STROKE_BOTTOM) for (x in STROKE_ON_GRAPHIC until STROKE_ON_GRAPHIC + 3) {
            card.setPixel(x, y, STROKE_OVER_GRAPHIC)
        }
        return card
    }

    private fun luminance(pixel: Int): Int =
        (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) +
            0.0722f * Color.blue(pixel)).roundToInt()

    private companion object {
        val CARD_FACE = Color.rgb(45, 125, 190)
        val CARD_GRAPHIC = Color.rgb(185, 215, 232)
        val STROKE_OVER_FACE = Color.rgb(150, 180, 200)
        val STROKE_OVER_GRAPHIC = Color.rgb(160, 190, 210)
        const val STROKE_ON_FACE = 80
        const val STROKE_ON_GRAPHIC = 320
        const val STROKE_TOP = 120
        const val STROKE_BOTTOM = 140
        // Mirrors ImagePreprocessor.NUMBER_LINE_ROW's top edge.
        const val LINE_TOP_RATIO = 0.44f
    }
}
