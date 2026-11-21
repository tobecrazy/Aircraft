package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.young.developtools.data.ScanHint

/**
 * Cheap frame-quality check used to nudge the user while scanning.
 *
 * The verdict is advisory: [com.young.developtools.viewmodel.CardScanViewModel]
 * still runs OCR on every frame, because an uncalibrated blur threshold that
 * silently drops frames would be worse than a wrong reading (the cross-frame
 * vote already filters those). Thresholds are deliberately conservative and
 * measured on a downscaled grayscale copy, which is also why they are much
 * lower than full-resolution Laplacian figures.
 */
internal object ImageQuality {

    private const val SAMPLE_WIDTH = 192
    private const val SAMPLE_HEIGHT = 108

    private const val DARK_MEAN = 32f
    private const val BRIGHT_MEAN = 247f
    private const val GLARE_LEVEL = 252
    private const val GLARE_RATIO = 0.5f
    private const val BLUR_VARIANCE = 6f

    /** Returns the most severe problem, or null when the frame looks usable. */
    fun check(bitmap: Bitmap): ScanHint? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val sample = bitmap.scale(SAMPLE_WIDTH, SAMPLE_HEIGHT)
        val width = sample.width
        val height = sample.height
        val pixels = IntArray(width * height)
        sample.getPixels(pixels, 0, width, 0, 0, width, height)
        if (sample !== bitmap) sample.recycle()

        val gray = IntArray(pixels.size)
        var total = 0L
        var glare = 0
        for (index in pixels.indices) {
            val color = pixels[index]
            val value = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color))
                .toInt()
                .coerceIn(0, 255)
            gray[index] = value
            total += value
            if (value >= GLARE_LEVEL) glare++
        }
        val mean = total.toFloat() / gray.size
        if (mean < DARK_MEAN) return ScanHint.DARK
        if (mean > BRIGHT_MEAN) return ScanHint.BRIGHT
        if (glare.toFloat() / gray.size > GLARE_RATIO) return ScanHint.GLARE

        // 4-neighbour Laplacian variance: high for sharp edges, near zero when blurred.
        var lapSum = 0.0
        var lapSquares = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val laplacian = 4.0 * gray[index] -
                    gray[index - 1] - gray[index + 1] - gray[index - width] - gray[index + width]
                lapSum += laplacian
                lapSquares += laplacian * laplacian
                count++
            }
        }
        if (count == 0) return null
        val lapMean = lapSum / count
        val variance = (lapSquares / count - lapMean * lapMean).toFloat()
        return if (variance < BLUR_VARIANCE) ScanHint.BLUR else null
    }
}
