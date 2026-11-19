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

    private const val ANALYSIS_LONG_SIDE = 96
    private const val CLAHE_TILES = 8
    private const val CLIP_RATIO = 0.025f
    private const val GOLD_RATIO_THRESHOLD = 0.04f
    private const val GLARE_RATIO_THRESHOLD = 0.01f
    // shortcut: uses a centered band, upgrade to card-boundary localization if varied layouts miss.
    private const val NUMBER_BAND_HEIGHT_RATIO = 0.42f
    private const val ADAPTIVE_RADIUS = 12
    private const val EDGE_THRESHOLD = 10

    /** Detects gold-like surfaces and clipped neutral highlights before OCR. */
    internal fun needsBankCardEnhancement(source: Bitmap): Boolean {
        val sample = enforceMaxLongSide(source, ANALYSIS_LONG_SIDE)
        val width = sample.width
        val height = sample.height
        val pixels = IntArray(width * height)
        sample.getPixels(pixels, 0, width, 0, 0, width, height)
        if (sample !== source) sample.recycle()

        var goldPixels = 0
        var glarePixels = 0
        for (pixel in pixels) {
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val spread = maxOf(red, green, blue) - minOf(red, green, blue)
            if (red >= 145 && green >= 105 && blue <= 175 && red - blue >= 35 && green - blue >= 20) {
                goldPixels++
            }
            val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue).toInt()
            if (luminance >= 220 && spread <= 50) glarePixels++
        }
        return goldPixels.toFloat() / pixels.size >= GOLD_RATIO_THRESHOLD ||
            glarePixels.toFloat() / pixels.size >= GLARE_RATIO_THRESHOLD
    }

    /** Enhances reflective cards and otherwise only applies the OCR size limit. */
    internal fun prepareBankCardForOcr(source: Bitmap, maxLongSide: Int): Bitmap =
        if (needsBankCardEnhancement(source)) enhanceForOcr(source, maxLongSide)
        else enforceMaxLongSide(source, maxLongSide)

    /** Crops the centered number band, then boosts local embossed edges for a fallback OCR pass. */
    internal fun prepareBankCardNumberBandForOcr(source: Bitmap, maxLongSide: Int): Bitmap {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val bandHeight = (bounded.height * NUMBER_BAND_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
        val top = (bounded.height - bandHeight) / 2
        val band = Bitmap.createBitmap(bounded, 0, top, bounded.width, bandHeight)
        if (bounded !== source && bounded !== band) bounded.recycle()
        return enhanceForOcr(band, maxLongSide).also { if (it !== band) band.recycle() }
    }

    /** Makes a locally thresholded edge image for embossed digits with little color contrast. */
    internal fun prepareBankCardNumberBandBinaryForOcr(source: Bitmap, maxLongSide: Int): Bitmap {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val bandHeight = (bounded.height * NUMBER_BAND_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
        val band = Bitmap.createBitmap(
            bounded, 0, (bounded.height - bandHeight) / 2, bounded.width, bandHeight
        )
        if (bounded !== source && bounded !== band) bounded.recycle()

        val width = band.width
        val height = band.height
        val pixels = IntArray(width * height)
        band.getPixels(pixels, 0, width, 0, 0, width, height)
        band.recycle()
        val gray = IntArray(pixels.size) { index ->
            val color = pixels[index]
            (0.2126f * Color.red(color) + 0.7152f * Color.green(color) +
                0.0722f * Color.blue(color)).roundToInt()
        }
        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += gray[y * width + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }
        val output = IntArray(gray.size)
        for (y in 0 until height) for (x in 0 until width) {
            val left = (x - ADAPTIVE_RADIUS).coerceAtLeast(0)
            val right = (x + ADAPTIVE_RADIUS + 1).coerceAtMost(width)
            val top = (y - ADAPTIVE_RADIUS).coerceAtLeast(0)
            val bottom = (y + ADAPTIVE_RADIUS + 1).coerceAtMost(height)
            val localSum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val localMean = localSum / ((right - left) * (bottom - top))
            output[y * width + x] = if (kotlin.math.abs(gray[y * width + x] - localMean) >= EDGE_THRESHOLD) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Rotate so the card's long edge is horizontal. Returns a NEW bitmap;
     * [source] is untouched — the caller keeps owning it and recycles it.
     * When [rotationDegrees] is 0 the source is returned as-is.
     */
    internal fun rotateToLandscape(source: Bitmap, rotationDegrees: Int): Bitmap {
        return rotate(source, rotationDegrees.toFloat())
    }

    /** Rotates by any angle. Returns [source] unchanged when the angle is zero. */
    internal fun rotate(source: Bitmap, rotationDegrees: Float): Bitmap {
        if (rotationDegrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** Rotates and bounds the result, recycling only intermediate bitmaps. */
    internal fun rotateForOcr(source: Bitmap, rotationDegrees: Float, maxLongSide: Int): Bitmap {
        val rotated = rotate(source, rotationDegrees)
        val bounded = enforceMaxLongSide(rotated, maxLongSide)
        if (rotated !== source && bounded !== rotated) rotated.recycle()
        return bounded
    }

    /**
     * Downscale so the long side is at most [maxLongSide], keeping aspect.
     * Does NOT recycle [bitmap]; the caller keeps owning it.
     */
    internal fun enforceMaxLongSide(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (w * scale).roundToInt(), (h * scale).roundToInt(), true
        )
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

        for (i in src.indices) {
            val color = src[i]
            src[i] = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) +
                0.0722f * Color.blue(color)).roundToInt().coerceIn(0, 255)
        }
        val grayscale = src.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val index = y * w + x
            val value = grayscale[index]
            val localMean = (grayscale[index - 1] + grayscale[index + 1] +
                grayscale[index - w] + grayscale[index + w]) / 4f
            src[index] = (value + (value - localMean) * 0.75f).roundToInt().coerceIn(0, 255)
        }

        val tileW = minOf(CLAHE_TILES, w)
        val tileH = minOf(CLAHE_TILES, h)
        val tileLuts = Array(tileW * tileH) { t ->
            val tx = t % tileW
            val ty = t / tileW
            val x0 = tx * w / tileW
            val y0 = ty * h / tileH
            val x1 = (tx + 1) * w / tileW
            val y1 = (ty + 1) * h / tileH
            // Clip histogram at CLIP_RATIO to stop one specular highlight dominating.
            val hist = IntArray(256)
            for (y in y0 until y1) for (x in x0 until x1) hist[src[y * w + x]]++
            val total = (x1 - x0) * (y1 - y0)
            val clip = (total * CLIP_RATIO).toInt().coerceAtLeast(1)
            var excess = 0
            for (i in 0..255) if (hist[i] > clip) { excess += hist[i] - clip; hist[i] = clip }
            val redistribute = excess / 256
            var remainder = excess % 256
            var cumulative = 0
            val lut = IntArray(256)
            for (v in 0..255) {
                cumulative += hist[v] + redistribute + if (remainder-- > 0) 1 else 0
                lut[v] = (cumulative.toFloat() / total * 255f).roundToInt().coerceIn(0, 255)
            }
            lut
        }

        val out = IntArray(w * h)
        for (y in 0 until h) {
            val gridY = (y + 0.5f) * tileH / h - 0.5f
            val floorY = kotlin.math.floor(gridY).toInt()
            val ty = floorY.coerceIn(0, tileH - 1)
            val tyNext = (floorY + 1).coerceIn(0, tileH - 1)
            val fy = if (floorY in 0 until tileH - 1) gridY - floorY else 0f
            for (x in 0 until w) {
                val gridX = (x + 0.5f) * tileW / w - 0.5f
                val floorX = kotlin.math.floor(gridX).toInt()
                val tx = floorX.coerceIn(0, tileW - 1)
                val txNext = (floorX + 1).coerceIn(0, tileW - 1)
                val fx = if (floorX in 0 until tileW - 1) gridX - floorX else 0f
                val v = src[y * w + x]
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
        // enforceMaxLongSide no longer recycles, so we own the scaled copy.
        if (input !== source) input.recycle()
        return result
    }
}
