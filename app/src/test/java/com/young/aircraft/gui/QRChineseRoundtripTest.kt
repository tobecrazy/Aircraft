package com.young.aircraft.gui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test

class QRChineseRoundtripTest {

    private fun encodeToBitMatrix(content: String, charset: String? = null): IntArray {
        val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 2)
        if (charset != null) hints[EncodeHintType.CHARACTER_SET] = charset
        val bitMatrix = MultiFormatWriter().encode(
            content, BarcodeFormat.QR_CODE, 512, 512, hints
        )
        val w = bitMatrix.width
        val h = bitMatrix.height
        val pixels = IntArray(w * h + 2)
        pixels[0] = w
        pixels[1] = h
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[2 + y * w + x] =
                    if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return pixels
    }

    private fun decodeFromPixels(pixels: IntArray, charset: String? = null): String {
        val w = pixels[0]
        val h = pixels[1]
        val imgPixels = pixels.copyOfRange(2, pixels.size)
        val source = RGBLuminanceSource(w, h, imgPixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = mutableMapOf<DecodeHintType, Any>(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        if (charset != null) hints[DecodeHintType.CHARACTER_SET] = charset
        return MultiFormatReader().decode(binaryBitmap, hints).text
    }

    @Test
    fun `Chinese roundtrip with UTF-8 encode and UTF-8 decode`() {
        val original = "你好世界 Hello"
        val pixels = encodeToBitMatrix(original, "UTF-8")
        assertEquals(original, decodeFromPixels(pixels, "UTF-8"))
    }

    @Test
    fun `Chinese roundtrip with UTF-8 encode and no charset decode`() {
        val original = "你好世界"
        val pixels = encodeToBitMatrix(original, "UTF-8")
        assertEquals(original, decodeFromPixels(pixels))
    }

    @Test
    fun `Chinese fails without UTF-8 on both sides`() {
        val original = "你好世界"
        val pixels = encodeToBitMatrix(original)
        val decoded = decodeFromPixels(pixels)
        assertNotEquals(
            "Without charset hint, Chinese is garbled",
            original, decoded
        )
    }

    @Test
    fun `mixed Chinese English roundtrip`() {
        val original = "Aircraft 飞机大战 v1.2.6"
        val pixels = encodeToBitMatrix(original, "UTF-8")
        assertEquals(original, decodeFromPixels(pixels, "UTF-8"))
    }
}
