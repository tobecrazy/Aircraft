package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.roundToInt

/**
 * Grayscale + CLAHE (lightweight) pre-processing for OCR.
 *
 * Metallic / embossed card faces make ML Kit emit nothing at all: the model
 * tolerates flat colour far better than high-frequency specular texture.
 * CLAHE's local contrast stretch flattens the gloss while pulling the digit
 * outlines out, so the digit go through the recognizer as ink-on-paper.
 *
 * Pure `android.graphics` + math, no new dependency. The CLAHE is the
 * 8x8-tile variant with bilinear blending and 2.5% top/bottom clipping.
 */
internal object ImagePreprocessor {

    private const val TILE = 8
    private const val CLIP_RATIO = 0.025f

    /** Rotate so the card's long edge is horizontal; caller owns recycling. */
    internal fun rotateToLandscape(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    /** Downscale so the long side is at most [maxLongSide], keeping aspect. */
    internal fun enforceMaxLongSide(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (w * scale).roundToInt(), (h * scale).roundToInt(), true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * Returns a NEW bitmap with the card-digit contrast boosted for OCR.
     * Caller owns recycling of [source] (untouched) and the result.
     */
    internal fun enhanceForOcr(source: Bitmap, maxLongSide: Int = 2048): Bitmap {
        val input = enforceMaxLongSide(source, maxLongSide)
        val w = input.width
        val h = input.height
        val src = IntArray(w * h)
        input.getPixels(src, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        for (i in src.indices) {
            val c = src[i]
            gray[i] = 0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)
        }

        val tileW = (w + TILE - 1) / TILE
        val tileH = (h + TILE - 1) / TILE
        val tileLuts = Array(tileW * tileH) { t ->
            val tx = t % tileW
            val ty = t / tileW
            val x0 = tx * TILE
            val y0 = ty * TILE
            val x1 = minOf(x0 + TILE, w)
            val y1 = minOf(y0 + TILE, h)
            // Clip histogram at CLIP_RATIO to stop one specular highlight dominating.
            val hist = IntArray(256)
            for (y in y0 until y1) for (x in x0 until x1) hist[gray[y * w + x].toInt().coerceIn(0, 255)]++
            val total = (x1 - x0) * (y1 - y0)
            val clip = (total * CLIP_RATIO).toInt().coerceAtLeast(1)
            var excess = 0
            for (i in 0..255) if (hist[i] > clip) { excess += hist[i] - clip; hist[i] = clip }
            val redistribute = excess / 256
            var cumulative = 0
            val lut = IntArray(256)
            for (v in 0..255) {
                cumulative += hist[v] + redistribute
                lut[v] = (cumulative.toFloat() / total * 255f).roundToInt().coerceIn(0, 255)
            }
            lut
        }

        val out = IntArray(w * h)
        for (y in 0 until h) {
            val ty = y / TILE
            val tyNext = minOf(ty + 1, tileH - 1)
            val fy = (y % TILE).toFloat() / TILE
            for (x in 0 until w) {
                val tx = x / TILE
                val txNext = minOf(tx + 1, tileW - 1)
                val fx = (x % TILE).toFloat() / TILE
                val v = gray[y * w + x].toInt().coerceIn(0, 255)
                val a = tileLuts[ty * tileW + tx][v]
                val b = tileLuts[ty * tileW + txNext][v]
                val c = tileLuts[tyNext * tileW + tx][v]
                val d = tileLuts[tyNext * tileW + txNext][v]
                val top = (a * (1 - fx) + b * fx).roundToInt()
                val bottom = (c * (1 - fx) + d * fx).roundToInt()
                val boosted = (top * (1 - fy) + bottom * fy).roundToInt()
                out[y * w + x] = Color.rgb(boosted, boosted, boosted)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (input !== source) input.recycle()
        return result
    }
}
