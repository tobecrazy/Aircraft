package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.young.developtools.data.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePreprocessorTest {

    @Test
    fun `offset card is localized and rectified to landscape`() {
        val photo = framedCard()

        val quad = ImagePreprocessor.detectCardQuad(photo)
        assertNotNull(quad)
        val region = ImagePreprocessor.localizeCardForOcr(photo, maxLongSide = 2048)

        assertTrue(region.rectified)
        assertTrue(region.bitmap.width > region.bitmap.height)
        assertEquals(
            1.586f,
            region.bitmap.width.toFloat() / region.bitmap.height,
            0.02f,
        )
        region.bitmap.recycle()
        photo.recycle()
    }

    @Test
    fun `sideways card is normalized to landscape`() {
        val photo = framedCard(rotation = 90f)

        val region = ImagePreprocessor.localizeCardForOcr(photo, maxLongSide = 2048)

        assertTrue(region.rectified)
        assertTrue(region.bitmap.width > region.bitmap.height)
        assertEquals(
            1.586f,
            region.bitmap.width.toFloat() / region.bitmap.height,
            0.02f,
        )
        region.bitmap.recycle()
        photo.recycle()
    }

    @Test
    fun `mildly skewed card is rectified`() {
        val photo = framedCard(perspective = true)

        val region = ImagePreprocessor.localizeCardForOcr(photo, maxLongSide = 2048)

        assertTrue(region.rectified)
        assertEquals(
            1.586f,
            region.bitmap.width.toFloat() / region.bitmap.height,
            0.02f,
        )
        region.bitmap.recycle()
        photo.recycle()
    }

    @Test
    fun `photo without a card falls back to the bounded source`() {
        val photo = solidBitmap(Color.rgb(210, 210, 210), width = 300, height = 200)

        val region = ImagePreprocessor.localizeCardForOcr(photo, maxLongSide = 2048)

        assertFalse(region.rectified)
        assertSame(photo, region.bitmap)
        assertNull(region.quad)
        photo.recycle()
    }

    @Test
    fun `number band binary pass isolates local embossed edges`() {
        val source = solidBitmap(Color.rgb(100, 100, 100), width = 96, height = 96)
        for (y in 30..70) for (x in 40..43) source.setPixel(x, y, Color.WHITE)

        val binary = ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(source, maxLongSide = 96)

        assertEquals(96, binary.width)
        // The default band is 0.34..0.76 of the card height.
        assertEquals(40, binary.height)
        assertEquals(Color.BLACK, binary.getPixel(41, 20))
        assertEquals(Color.WHITE, binary.getPixel(10, 20))
        source.recycle()
        binary.recycle()
    }

    @Test
    fun `number line flattening lifts strokes off a bright card graphic`() {
        val card = embossedBlueCard()

        val flattened = ImagePreprocessor.prepareBankCardNumberLineForOcr(card, maxLongSide = 2048)
        val binary = checkNotNull(
            ImagePreprocessor.prepareBankCardNumberLineAdaptiveForOcr(card, maxLongSide = 2048)
        )

        // The tight crop is enlarged up to the cap and never past it.
        assertEquals(2048, flattened.width)
        assertTrue(flattened.height in 1..2048)
        assertEquals(2048, binary.width)
        assertTrue(binary.height in 1..2048)

        val scale = flattened.width.toFloat() / card.width
        val lineTop = (card.height * LINE_TOP_RATIO).roundToInt()
        // Sample the row through the middle of the embossed strokes, not the crop centre.
        val strokeRow = (((STROKE_TOP + STROKE_BOTTOM) / 2f - lineTop) * scale).toInt()
        val onCardFace = ((STROKE_ON_FACE + 1.5f) * scale).toInt()
        val onGraphic = ((STROKE_ON_GRAPHIC + 1.5f) * scale).toInt()

        // Both polarities are lifted clear of the flattened mid-grey: the stroke over the
        // blue face is brighter, the one over the printed graphic is darker.
        val faceLevel = luminance(flattened.getPixel(onCardFace, strokeRow))
        val graphicLevel = luminance(flattened.getPixel(onGraphic, strokeRow))
        assertTrue("face stroke level $faceLevel", faceLevel >= 128 + 40)
        assertTrue("graphic stroke level $graphicLevel", graphicLevel <= 128 - 20)
        assertEquals(Color.BLACK, binary.getPixel(onCardFace, strokeRow))
        assertEquals(Color.BLACK, binary.getPixel(onGraphic, strokeRow))
        // The bare card face stays paper.
        assertEquals(Color.WHITE, binary.getPixel((20 * scale).toInt(), strokeRow))

        card.recycle()
        flattened.recycle()
        binary.recycle()
    }

    @Test
    fun `number row follows the OCR line box`() {
        val row = ImagePreprocessor.numberRowFromLines(
            listOf(OcrLine("4242 4242 4242 4242", top = 500, bottom = 560)),
            cardWidth = 1000,
            cardHeight = 1000,
        )

        assertNotNull(row)
        assertTrue(row!!.topRatio < 0.55f)
        assertTrue(row.bottomRatio > 0.55f)
    }

    @Test
    fun `line without enough digits does not define a number row`() {
        val row = ImagePreprocessor.numberRowFromLines(
            listOf(OcrLine("VALID THRU 12/29", top = 500, bottom = 560)),
            cardWidth = 1000,
            cardHeight = 1000,
        )

        assertNull(row)
    }

    @Test
    fun `rotating and bounding never exceeds the cap`() {
        val source = solidBitmap(Color.GRAY, width = 400, height = 260)

        val rotated = ImagePreprocessor.rotateForOcr(source, 90f, maxLongSide = 128)

        assertNotSame(source, rotated)
        assertEquals(128, maxOf(rotated.width, rotated.height))
        source.recycle()
        rotated.recycle()
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

    /** A card drawn onto a larger table-coloured photo, optionally rotated or skewed. */
    private fun framedCard(
        rotation: Float = 0f,
        perspective: Boolean = false,
    ): Bitmap {
        val photo = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        photo.eraseColor(TABLE)
        val card = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        card.eraseColor(CARD_FACE)
        for (y in 0 until CARD_HEIGHT) for (x in CARD_WIDTH * 2 / 3 until CARD_WIDTH) {
            card.setPixel(x, y, CARD_GRAPHIC)
        }
        val rowTop = (CARD_HEIGHT * 0.46f).toInt()
        val rowBottom = (CARD_HEIGHT * 0.60f).toInt()
        for (y in rowTop until rowBottom) for (x in CARD_WIDTH / 8 until CARD_WIDTH * 7 / 8) {
            card.setPixel(x, y, NUMBER_ROW)
        }

        val canvas = Canvas(photo)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when {
            perspective -> {
                val matrix = Matrix()
                matrix.setPolyToPoly(
                    floatArrayOf(
                        0f, 0f,
                        CARD_WIDTH.toFloat(), 0f,
                        CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                        0f, CARD_HEIGHT.toFloat(),
                    ),
                    0,
                    floatArrayOf(
                        CARD_LEFT.toFloat(), CARD_TOP.toFloat(),
                        (CARD_LEFT + CARD_WIDTH).toFloat(), CARD_TOP - 20f,
                        (CARD_LEFT + CARD_WIDTH + 20).toFloat(), CARD_TOP + CARD_HEIGHT - 20f,
                        CARD_LEFT - 20f, CARD_TOP + CARD_HEIGHT.toFloat(),
                    ),
                    0,
                    4,
                )
                canvas.drawBitmap(card, matrix, paint)
            }
            rotation != 0f -> {
                canvas.save()
                canvas.rotate(rotation, CARD_LEFT + CARD_WIDTH / 2f, CARD_TOP + CARD_HEIGHT / 2f)
                canvas.drawBitmap(card, CARD_LEFT.toFloat(), CARD_TOP.toFloat(), paint)
                canvas.restore()
            }
            else -> canvas.drawBitmap(card, CARD_LEFT.toFloat(), CARD_TOP.toFloat(), paint)
        }
        card.recycle()
        return photo
    }

    private fun luminance(pixel: Int): Int =
        (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) +
            0.0722f * Color.blue(pixel)).roundToInt()

    private fun solidBitmap(color: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        val CARD_FACE = Color.rgb(45, 125, 190)
        val CARD_GRAPHIC = Color.rgb(185, 215, 232)
        val STROKE_OVER_FACE = Color.rgb(150, 180, 200)
        val STROKE_OVER_GRAPHIC = Color.rgb(160, 190, 210)
        val TABLE = Color.rgb(235, 235, 235)
        val NUMBER_ROW = Color.rgb(200, 215, 225)
        const val STROKE_ON_FACE = 80
        const val STROKE_ON_GRAPHIC = 320
        const val STROKE_TOP = 120
        const val STROKE_BOTTOM = 140
        const val CARD_WIDTH = 420
        const val CARD_HEIGHT = 265
        const val CARD_LEFT = 170
        const val CARD_TOP = 150
        // Mirrors ImagePreprocessor.NUMBER_LINE_ROW's top edge.
        const val LINE_TOP_RATIO = 0.44f
    }
}
