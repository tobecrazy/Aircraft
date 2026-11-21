package com.young.developtools.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.young.developtools.data.OcrLine
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Card-face localization and OCR pre-processing.
 *
 * The scanner no longer assumes the card and its number line are centred in
 * the photo. [localizeCardForOcr] estimates the card quadrilateral from the
 * colour contrast between the card and the surface around it, orders the four
 * corners, and perspective-warps the card into a landscape image with the
 * standard ID-1 aspect ratio. Every later crop is expressed in card-normalized
 * coordinates, so an off-centre or mildly skewed photo is handled the same way
 * as a centred one.
 *
 * Detection uses OpenCV contours when the native library loads and a pure
 * Kotlin connected-component pass otherwise, which keeps unit tests and
 * unsupported ABIs on the same geometry. The Kotlin filters stay as the
 * fallback for the pixel variants: CLAHE for local contrast, and illumination
 * flattening plus stroke thresholding for embossed digits whose ink matches
 * the card face.
 *
 * Nothing here stores or uploads pixels or recognized text.
 */
internal object ImagePreprocessor {

    private const val CLAHE_TILES = 8
    private const val CLIP_RATIO = 0.025f
    private const val ADAPTIVE_RADIUS = 12
    private const val EDGE_THRESHOLD = 10
    // Background window for illumination flattening: wide enough to ignore a stroke.
    private const val ILLUMINATION_WINDOW_DIVISOR = 64
    private const val ILLUMINATION_MIN_RADIUS = 8
    private const val ILLUMINATION_GAIN = 2f
    // On the flattened image the card face sits at this level and raised strokes above it.
    private const val FLATTENED_LEVEL = 128
    // Half the amplification-adjusted noise floor: a stroke only has to clear ~8 grey levels.
    private const val STROKE_LEVEL_DELTA = 16
    // A flattened ROI outside this band has no digit strokes worth thresholding:
    // below it there is nothing, above it the card artwork dominates.
    private const val MIN_STROKE_SIGNAL_RATIO = 0.0015f
    private const val MAX_STROKE_SIGNAL_RATIO = 0.35f

    // Card detection runs on a downscaled copy; 480 px keeps a card edge accurate
    // to a few source pixels without the connected-component pass getting costly.
    private const val CARD_ANALYSIS_LONG_SIDE = 480
    private const val CARD_BACKGROUND_DISTANCE = 44f
    private const val MIN_CARD_AREA_RATIO = 0.10f
    private const val MIN_CARD_EDGE_RATIO = 0.12f
    private const val MAX_EDGE_PAIR_RATIO = 1.9f
    private const val MIN_CARD_ASPECT = 1.15f
    private const val MAX_CARD_ASPECT = 3.2f
    private const val MIN_CORNER_ANGLE = 55f
    private const val MAX_CORNER_ANGLE = 125f
    // ISO/IEC 7810 ID-1: 85.60 x 53.98 mm.
    private const val CARD_ASPECT_RATIO = 1.586f
    private const val MIN_RECTIFIED_WIDTH = 640

    // Card-normalized rows. A number line normally sits a little below the
    // vertical centre; the band is wide enough to survive layout variation.
    internal val NUMBER_BAND_ROW = NumberRow(0.34f, 0.76f)
    internal val NUMBER_LINE_ROW = NumberRow(0.44f, 0.66f)
    internal val UPPER_NUMBER_LINE_ROW = NumberRow(0.36f, 0.56f)
    internal val LOWER_NUMBER_LINE_ROW = NumberRow(0.56f, 0.76f)
    private const val MIN_ROW_DIGITS = 6

    /** A horizontal slice of a card, expressed as fractions of its height. */
    internal data class NumberRow(val topRatio: Float, val bottomRatio: Float) {
        /** Widens the row so a band keeps the line plus some card face around it. */
        fun band(): NumberRow = NumberRow(
            (topRatio - 0.08f).coerceAtLeast(0.20f),
            (bottomRatio + 0.10f).coerceAtMost(0.85f),
        )
    }

    /** Card corners in source-image pixels, ordered clockwise from the top-left. */
    internal data class CardQuad(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomRight: PointF,
        val bottomLeft: PointF,
    ) {
        fun points(): FloatArray = floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y,
        )

        fun scaled(scaleX: Float, scaleY: Float): CardQuad = CardQuad(
            PointF(topLeft.x * scaleX, topLeft.y * scaleY),
            PointF(topRight.x * scaleX, topRight.y * scaleY),
            PointF(bottomRight.x * scaleX, bottomRight.y * scaleY),
            PointF(bottomLeft.x * scaleX, bottomLeft.y * scaleY),
        )

        /** Rotates the corner order so the card's long edge is horizontal. */
        fun landscape(): CardQuad {
            val horizontal = distance(topLeft, topRight) + distance(bottomLeft, bottomRight)
            val vertical = distance(topLeft, bottomLeft) + distance(topRight, bottomRight)
            if (horizontal >= vertical) return this
            return CardQuad(
                topLeft = bottomLeft,
                topRight = topLeft,
                bottomRight = topRight,
                bottomLeft = bottomRight,
            )
        }
    }

    /**
     * Result of [localizeCardForOcr]. [rectified] is true when [bitmap] is a
     * perspective-corrected card; when false it is the bounded source and the
     * caller should fall back to the rotation sweep.
     */
    internal data class CardRegion(
        val bitmap: Bitmap,
        val rectified: Boolean,
        val quad: CardQuad?,
    )

    /**
     * Estimates the card quadrilateral and warps it to a landscape image.
     * Returns the bounded source unchanged when no reliable card is found.
     * The caller owns [CardRegion.bitmap] and recycles it when it is not the
     * original [source].
     */
    internal fun localizeCardForOcr(source: Bitmap, maxLongSide: Int): CardRegion {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val quad = runCatching { detectCardQuad(bounded) }.getOrNull()
        val rectified = quad?.let {
            runCatching { rectifyCard(bounded, it, maxLongSide) }.getOrNull()
        }
        return if (rectified != null) {
            if (bounded !== source && bounded !== rectified) bounded.recycle()
            CardRegion(rectified, true, quad)
        } else {
            CardRegion(bounded, false, null)
        }
    }

    /**
     * Finds the largest card-like quadrilateral in [source]. Coordinates are in
     * source pixels; null when no candidate passes the shape checks.
     */
    internal fun detectCardQuad(source: Bitmap): CardQuad? {
        if (source.width < 32 || source.height < 32) return null
        val analysis = enforceMaxLongSide(source, CARD_ANALYSIS_LONG_SIDE)
        try {
            val width = analysis.width
            val height = analysis.height
            val pixels = IntArray(width * height)
            analysis.getPixels(pixels, 0, width, 0, 0, width, height)
            val mask = buildCardMask(pixels, width, height) ?: return null
            val cleaned = openMask(mask, width, height)
            // The card must reach the middle of the frame. When the border
            // median is the card itself (a frame-filling photo), the mask marks
            // the background instead and the centre is empty, so warping would
            // capture the table rather than the card.
            if (!isCentreCovered(cleaned, width, height)) return null
            val openCvQuad = if (OpenCvSupport.isAvailable()) {
                openCvLargestQuad(cleaned, width, height)
            } else {
                null
            }
            // A contour that fails the shape checks must not shadow the Kotlin
            // component result: both read the same mask, but they can disagree
            // on which blob is largest when a shadow touches the card.
            val quad = openCvQuad?.takeIf { isPlausibleCardQuad(it, width, height) }
                ?: kotlinLargestQuad(cleaned, width, height)
                    ?.takeIf { isPlausibleCardQuad(it, width, height) }
                ?: return null
            return quad.scaled(
                source.width.toFloat() / width,
                source.height.toFloat() / height,
            )
        } finally {
            if (analysis !== source) analysis.recycle()
        }
    }

    /**
     * Perspective-warps [quad] onto a landscape bitmap. Returns null when the
     * transform cannot be built; the caller then keeps the unrectified source.
     */
    internal fun rectifyCard(source: Bitmap, quad: CardQuad, maxLongSide: Int): Bitmap? {
        val landscape = quad.landscape()
        val top = distance(landscape.topLeft, landscape.topRight)
        val bottom = distance(landscape.bottomLeft, landscape.bottomRight)
        val left = distance(landscape.topLeft, landscape.bottomLeft)
        val right = distance(landscape.topRight, landscape.bottomRight)
        val longEdge = maxOf((top + bottom) / 2f, (left + right) / 2f)
        val minimumWidth = MIN_RECTIFIED_WIDTH.coerceAtMost(maxLongSide)
        val width = longEdge.roundToInt().coerceIn(minimumWidth, maxLongSide)
        val height = (width / CARD_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
        val points = landscape.points()
        return if (OpenCvSupport.isAvailable()) {
            runCatching { openCvWarp(source, points, width, height) }.getOrNull()
        } else {
            matrixWarp(source, points, width, height)
        }
    }

    /**
     * Picks the row of the OCR line that most looks like the card number, or
     * null when no line carries enough digits. Used to refine the line crop
     * after a successful full-card pass.
     */
    internal fun numberRowFromLines(
        lines: List<OcrLine>,
        cardWidth: Int,
        cardHeight: Int,
    ): NumberRow? {
        if (cardWidth <= 0 || cardHeight <= 0) return null
        val best = lines.asSequence()
            .filter { it.bottom > it.top && it.text.count(Char::isDigit) >= MIN_ROW_DIGITS }
            .maxByOrNull { it.text.count(Char::isDigit) } ?: return null
        val center = ((best.top + best.bottom) / 2f) / cardHeight
        if (center !in 0.15f..0.85f) return null
        val half = (((best.bottom - best.top).toFloat() / cardHeight) * 1.3f)
            .coerceIn(0.07f, 0.20f)
        val top = (center - half).coerceIn(0.15f, 0.80f)
        val bottom = (center + half).coerceIn(top + 0.05f, 0.85f)
        return NumberRow(top, bottom)
    }

    /** Pixel bounds of [row] inside a card image, as left/top/right/bottom. */
    internal fun rowCropBounds(cardWidth: Int, cardHeight: Int, row: NumberRow): IntArray {
        val top = (cardHeight * row.topRatio).roundToInt().coerceIn(0, cardHeight - 1)
        val bottom = (cardHeight * row.bottomRatio).roundToInt().coerceIn(top + 1, cardHeight)
        return intArrayOf(0, top, cardWidth, bottom)
    }

    /** Crops the number band, then boosts local embossed edges for a fallback OCR pass. */
    internal fun prepareBankCardNumberBandForOcr(
        source: Bitmap,
        maxLongSide: Int,
        row: NumberRow = NUMBER_BAND_ROW,
    ): Bitmap {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val band = cropRow(bounded, row)
        if (bounded !== source && bounded !== band) bounded.recycle()
        return enhanceForOcr(band, maxLongSide).also { if (it !== band) band.recycle() }
    }

    /** Makes a locally thresholded edge image for embossed digits with little color contrast. */
    internal fun prepareBankCardNumberBandBinaryForOcr(
        source: Bitmap,
        maxLongSide: Int,
        row: NumberRow = NUMBER_BAND_ROW,
    ): Bitmap {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val band = cropRow(bounded, row)
        if (bounded !== source && bounded !== band) bounded.recycle()
        return adaptiveThresholdBitmap(band, ADAPTIVE_RADIUS, EDGE_THRESHOLD)
            .also { if (it !== band) band.recycle() }
    }

    /**
     * Crops the number line, enlarges it to the size cap, then flattens the
     * card face's own brightness variation.
     *
     * Embossed digits tinted close to the card face have almost no contrast on
     * the raw crop, and the part of the number that crosses a printed graphic
     * (a map, a hologram, a light patch) has less. CLAHE cannot recover that —
     * on a band that is mostly one flat colour its equalisation table is close
     * to the identity. Subtracting a blurred copy and amplifying the difference
     * turns every stroke into a relief against a uniform mid-grey, whatever the
     * background brightness.
     *
     * Returns a NEW bitmap; [source] is untouched.
     */
    internal fun prepareBankCardNumberLineForOcr(
        source: Bitmap,
        maxLongSide: Int,
        row: NumberRow = NUMBER_LINE_ROW,
    ): Bitmap {
        val enlarged = enlargeNumberLine(source, maxLongSide, row)
        val result = if (OpenCvSupport.isAvailable()) {
            openCvFlattenIllumination(enlarged)
        } else {
            flattenIlluminationBitmap(enlarged)
        }
        if (result !== enlarged) enlarged.recycle()
        return result
    }

    /**
     * Binarized counterpart of [prepareBankCardNumberLineForOcr]: after
     * flattening, the card face sits at [FLATTENED_LEVEL] and only the strokes
     * deviate from it, in either direction (a raised digit is brighter than the
     * face, its shadowed edge darker). Because the background window is far
     * wider than a stroke, the stroke centre keeps most of the deviation and
     * comes out solid rather than outlined.
     *
     * Returns null when the flattened ROI has no usable stroke signal, so the
     * caller can skip an OCR pass that would only see amplified card artwork.
     * Otherwise returns a NEW bitmap; [source] is untouched.
     */
    internal fun prepareBankCardNumberLineAdaptiveForOcr(
        source: Bitmap,
        maxLongSide: Int,
        row: NumberRow = NUMBER_LINE_ROW,
    ): Bitmap? {
        val enlarged = enlargeNumberLine(source, maxLongSide, row)
        val flattened = if (OpenCvSupport.isAvailable()) {
            openCvFlattenIllumination(enlarged)
        } else {
            flattenIlluminationBitmap(enlarged)
        }
        if (flattened !== enlarged) enlarged.recycle()
        if (!hasUsableStrokeSignal(flattened)) {
            flattened.recycle()
            return null
        }
        val result = if (OpenCvSupport.isAvailable()) {
            openCvStrokeThreshold(flattened)
        } else {
            strokeThresholdBitmap(flattened)
        }
        if (result !== flattened) flattened.recycle()
        return result
    }

    /** Samples the flattened ROI: too few or too many deviations mean no digit strokes. */
    private fun hasUsableStrokeSignal(source: Bitmap): Boolean {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return false
        val stepX = (width / 240).coerceAtLeast(1)
        val stepY = (height / 240).coerceAtLeast(1)
        var sampled = 0
        var strokes = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (abs(Color.red(source.getPixel(x, y)) - FLATTENED_LEVEL) > STROKE_LEVEL_DELTA) {
                    strokes++
                }
                sampled++
                x += stepX
            }
            y += stepY
        }
        if (sampled == 0) return false
        val ratio = strokes.toFloat() / sampled
        return ratio in MIN_STROKE_SIGNAL_RATIO..MAX_STROKE_SIGNAL_RATIO
    }

    /** Crops [row], then scales the long side to [maxLongSide]. */
    private fun enlargeNumberLine(source: Bitmap, maxLongSide: Int, row: NumberRow): Bitmap {
        val bounded = enforceMaxLongSide(source, maxLongSide)
        val line = cropRow(bounded, row)
        if (bounded !== source && bounded !== line) bounded.recycle()
        val longest = maxOf(line.width, line.height)
        if (longest == maxLongSide) return line
        val scale = maxLongSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            line,
            (line.width * scale).roundToInt().coerceAtLeast(1),
            (line.height * scale).roundToInt().coerceAtLeast(1),
            true
        ).also { if (it !== line) line.recycle() }
    }

    private fun cropRow(source: Bitmap, row: NumberRow): Bitmap {
        val bounds = rowCropBounds(source.width, source.height, row)
        return Bitmap.createBitmap(
            source,
            bounds[0],
            bounds[1],
            bounds[2] - bounds[0],
            bounds[3] - bounds[1],
        )
    }

    // ---------------------------------------------------------------------
    // Card detection
    // ---------------------------------------------------------------------

    /**
     * Marks pixels whose colour differs from the border median. The border is
     * sampled on all four sides so a card that touches one edge does not
     * dominate the estimate; the median rejects a card corner that reaches
     * into the ring.
     */
    private fun buildCardMask(pixels: IntArray, width: Int, height: Int): ByteArray? {
        val border = (minOf(width, height) / 24).coerceIn(2, 24)
        val redHistogram = IntArray(256)
        val greenHistogram = IntArray(256)
        val blueHistogram = IntArray(256)
        var samples = 0

        fun sample(x: Int, y: Int) {
            val color = pixels[y * width + x]
            redHistogram[Color.red(color)]++
            greenHistogram[Color.green(color)]++
            blueHistogram[Color.blue(color)]++
            samples++
        }

        for (y in 0 until border) {
            for (x in 0 until width) {
                sample(x, y)
                sample(x, height - 1 - y)
            }
        }
        for (x in 0 until border) {
            for (y in border until height - border) {
                sample(x, y)
                sample(width - 1 - x, y)
            }
        }
        if (samples == 0) return null

        val backgroundRed = medianFromHistogram(redHistogram, samples)
        val backgroundGreen = medianFromHistogram(greenHistogram, samples)
        val backgroundBlue = medianFromHistogram(blueHistogram, samples)
        val thresholdSq = CARD_BACKGROUND_DISTANCE * CARD_BACKGROUND_DISTANCE

        val mask = ByteArray(pixels.size)
        var cardPixels = 0
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = Color.red(color) - backgroundRed
            val green = Color.green(color) - backgroundGreen
            val blue = Color.blue(color) - backgroundBlue
            if (red * red + green * green + blue * blue >= thresholdSq) {
                mask[index] = 1
                cardPixels++
            }
        }
        if (cardPixels < pixels.size * MIN_CARD_AREA_RATIO) return null
        return mask
    }

    private fun medianFromHistogram(histogram: IntArray, total: Int): Int {
        val target = total / 2
        var cumulative = 0
        for (value in histogram.indices) {
            cumulative += histogram[value]
            if (cumulative > target) return value
        }
        return 0
    }

    /** 3x3 opening: drops isolated texture without eroding the card away. */
    private fun openMask(mask: ByteArray, width: Int, height: Int): ByteArray {
        if (width < 3 || height < 3) return mask
        val eroded = ByteArray(mask.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (mask[index].toInt() == 0) continue
                if (mask[index - 1].toInt() != 0 &&
                    mask[index + 1].toInt() != 0 &&
                    mask[index - width].toInt() != 0 &&
                    mask[index + width].toInt() != 0
                ) {
                    eroded[index] = 1
                }
            }
        }
        val dilated = ByteArray(mask.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (eroded[index].toInt() != 0 ||
                    eroded[index - 1].toInt() != 0 ||
                    eroded[index + 1].toInt() != 0 ||
                    eroded[index - width].toInt() != 0 ||
                    eroded[index + width].toInt() != 0
                ) {
                    dilated[index] = 1
                }
            }
        }
        return dilated
    }

    /** True when any foreground pixel sits near the frame centre. */
    private fun isCentreCovered(mask: ByteArray, width: Int, height: Int): Boolean {
        // 20% of the short side tolerates an off-centre card while still
        // rejecting the background margin of a frame-filling photo.
        val radius = (minOf(width, height) * 0.20f).roundToInt().coerceAtLeast(4)
        val centreX = width / 2
        val centreY = height / 2
        val left = (centreX - radius).coerceAtLeast(0)
        val right = (centreX + radius).coerceAtMost(width - 1)
        val top = (centreY - radius).coerceAtLeast(0)
        val bottom = (centreY + radius).coerceAtMost(height - 1)
        for (y in top..bottom) {
            for (x in left..right) {
                if (mask[y * width + x].toInt() != 0) return true
            }
        }
        return false
    }

    /** OpenCV path: largest external contour, reduced to its four extreme points. */
    private fun openCvLargestQuad(mask: ByteArray, width: Int, height: Int): CardQuad? {
        val binary = Mat(height, width, CvType.CV_8UC1)
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            val scaled = ByteArray(mask.size) {
                if (mask[it].toInt() != 0) 255.toByte() else 0
            }
            binary.put(0, 0, scaled)
            Imgproc.findContours(
                binary,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )
            var largest: MatOfPoint? = null
            var largestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > largestArea) {
                    largestArea = area
                    largest = contour
                }
            }
            val points = largest?.toArray() ?: return null
            if (points.size < 4) return null
            val accumulator = QuadAccumulator()
            points.forEach { accumulator.add(it.x.toFloat(), it.y.toFloat()) }
            return accumulator.toQuad()
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
            binary.release()
        }
    }

    /** Kotlin path: 4-connected components, largest one reduced to its extremes. */
    private fun kotlinLargestQuad(mask: ByteArray, width: Int, height: Int): CardQuad? {
        val labels = IntArray(mask.size) { -1 }
        val stack = IntArray(mask.size)
        var label = 0
        var bestLabel = -1
        var bestSize = 0

        for (start in mask.indices) {
            if (mask[start].toInt() == 0 || labels[start] != -1) continue
            var top = 0
            stack[top++] = start
            labels[start] = label
            var size = 0
            while (top > 0) {
                val index = stack[--top]
                size++
                val x = index % width
                val y = index / width
                if (x > 0) {
                    val next = index - 1
                    if (mask[next].toInt() != 0 && labels[next] == -1) {
                        labels[next] = label
                        stack[top++] = next
                    }
                }
                if (x < width - 1) {
                    val next = index + 1
                    if (mask[next].toInt() != 0 && labels[next] == -1) {
                        labels[next] = label
                        stack[top++] = next
                    }
                }
                if (y > 0) {
                    val next = index - width
                    if (mask[next].toInt() != 0 && labels[next] == -1) {
                        labels[next] = label
                        stack[top++] = next
                    }
                }
                if (y < height - 1) {
                    val next = index + width
                    if (mask[next].toInt() != 0 && labels[next] == -1) {
                        labels[next] = label
                        stack[top++] = next
                    }
                }
            }
            if (size > bestSize) {
                bestSize = size
                bestLabel = label
            }
            label++
        }
        if (bestLabel < 0) return null

        val accumulator = QuadAccumulator()
        for (index in mask.indices) {
            if (labels[index] == bestLabel) {
                accumulator.add((index % width).toFloat(), (index / width).toFloat())
            }
        }
        return accumulator.toQuad()
    }

    /** Collects the four extreme points of a convex point set. */
    private class QuadAccumulator {
        private var topLeft: PointF? = null
        private var topRight: PointF? = null
        private var bottomRight: PointF? = null
        private var bottomLeft: PointF? = null
        private var minSum = Float.MAX_VALUE
        private var maxSum = -Float.MAX_VALUE
        private var minDifference = Float.MAX_VALUE
        private var maxDifference = -Float.MAX_VALUE

        fun add(x: Float, y: Float) {
            val sum = x + y
            val difference = x - y
            if (sum < minSum) {
                minSum = sum
                topLeft = PointF(x, y)
            }
            if (sum > maxSum) {
                maxSum = sum
                bottomRight = PointF(x, y)
            }
            if (difference > maxDifference) {
                maxDifference = difference
                topRight = PointF(x, y)
            }
            if (difference < minDifference) {
                minDifference = difference
                bottomLeft = PointF(x, y)
            }
        }

        fun toQuad(): CardQuad? {
            val topLeft = topLeft ?: return null
            val topRight = topRight ?: return null
            val bottomRight = bottomRight ?: return null
            val bottomLeft = bottomLeft ?: return null
            return CardQuad(topLeft, topRight, bottomRight, bottomLeft)
        }
    }

    private fun isPlausibleCardQuad(quad: CardQuad, width: Int, height: Int): Boolean {
        val points = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        if (points.any { it.x < -2f || it.x > width + 2f || it.y < -2f || it.y > height + 2f }) {
            return false
        }
        val area = polygonArea(points)
        if (area < width * height * MIN_CARD_AREA_RATIO) return false

        val top = distance(quad.topLeft, quad.topRight)
        val right = distance(quad.topRight, quad.bottomRight)
        val bottom = distance(quad.bottomLeft, quad.bottomRight)
        val left = distance(quad.topLeft, quad.bottomLeft)
        val minEdge = minOf(top, right, bottom, left)
        if (minEdge < minOf(width, height) * MIN_CARD_EDGE_RATIO) return false
        if (max(top, bottom) / min(top, bottom).coerceAtLeast(1f) > MAX_EDGE_PAIR_RATIO) return false
        if (max(left, right) / min(left, right).coerceAtLeast(1f) > MAX_EDGE_PAIR_RATIO) return false

        val averageWidth = (top + bottom) / 2f
        val averageHeight = (left + right) / 2f
        val aspect = max(averageWidth, averageHeight) /
            min(averageWidth, averageHeight).coerceAtLeast(1f)
        if (aspect !in MIN_CARD_ASPECT..MAX_CARD_ASPECT) return false

        val angles = listOf(
            angleAt(quad.bottomLeft, quad.topLeft, quad.topRight),
            angleAt(quad.topLeft, quad.topRight, quad.bottomRight),
            angleAt(quad.topRight, quad.bottomRight, quad.bottomLeft),
            angleAt(quad.bottomRight, quad.bottomLeft, quad.topLeft),
        )
        return angles.all { it in MIN_CORNER_ANGLE..MAX_CORNER_ANGLE }
    }

    private fun polygonArea(points: List<PointF>): Float {
        var sum = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            sum += current.x * next.y - next.x * current.y
        }
        return abs(sum) / 2f
    }

    private fun angleAt(previous: PointF, corner: PointF, next: PointF): Float {
        val firstX = previous.x - corner.x
        val firstY = previous.y - corner.y
        val secondX = next.x - corner.x
        val secondY = next.y - corner.y
        val magnitude = hypot(firstX, firstY) * hypot(secondX, secondY)
        if (magnitude == 0f) return 0f
        val cosine = ((firstX * secondX + firstY * secondY) / magnitude).coerceIn(-1f, 1f)
        return acos(cosine) * 180f / Math.PI.toFloat()
    }

    private fun distance(first: PointF, second: PointF): Float =
        hypot(first.x - second.x, first.y - second.y)

    // ---------------------------------------------------------------------
    // Perspective warp
    // ---------------------------------------------------------------------

    private fun matrixWarp(source: Bitmap, srcPoints: FloatArray, width: Int, height: Int): Bitmap? {
        val dstPoints = floatArrayOf(
            0f, 0f,
            (width - 1).toFloat(), 0f,
            (width - 1).toFloat(), (height - 1).toFloat(),
            0f, (height - 1).toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)) return null
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // White, not transparent: a transparent edge becomes black in ML Kit.
        output.eraseColor(Color.WHITE)
        Canvas(output).drawBitmap(
            source,
            matrix,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return output
    }

    private fun openCvWarp(source: Bitmap, srcPoints: FloatArray, width: Int, height: Int): Bitmap {
        val src = Mat(4, 1, CvType.CV_32FC2)
        val dst = Mat(4, 1, CvType.CV_32FC2)
        val rgba = Mat()
        val warped = Mat()
        var transform: Mat? = null
        try {
            src.put(0, 0, srcPoints)
            dst.put(
                0,
                0,
                floatArrayOf(
                    0f, 0f,
                    (width - 1).toFloat(), 0f,
                    (width - 1).toFloat(), (height - 1).toFloat(),
                    0f, (height - 1).toFloat(),
                ),
            )
            val perspective = Imgproc.getPerspectiveTransform(src, dst)
            transform = perspective
            Utils.bitmapToMat(source, rgba)
            Imgproc.warpPerspective(
                rgba,
                warped,
                perspective,
                Size(width.toDouble(), height.toDouble()),
                Imgproc.INTER_LINEAR,
                Core.BORDER_CONSTANT,
                Scalar(255.0, 255.0, 255.0, 255.0),
            )
            return warped.toBitmap()
        } finally {
            src.release()
            dst.release()
            transform?.release()
            rgba.release()
            warped.release()
        }
    }

    // ---------------------------------------------------------------------
    // Pixel filters
    // ---------------------------------------------------------------------

    /** Integral-image local-mean threshold: strokes become black on white. */
    private fun adaptiveThresholdBitmap(source: Bitmap, radius: Int, threshold: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
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
            val left = (x - radius).coerceAtLeast(0)
            val right = (x + radius + 1).coerceAtMost(width)
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(height)
            val localSum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val localMean = localSum / ((right - left) * (bottom - top))
            output[y * width + x] = if (abs(gray[y * width + x] - localMean) >= threshold) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    /** Grayscale minus its blurred self, amplified around mid-grey. */
    private fun flattenIlluminationBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = IntArray(pixels.size) { index ->
            val color = pixels[index]
            (0.2126f * Color.red(color) + 0.7152f * Color.green(color) +
                0.0722f * Color.blue(color)).roundToInt()
        }
        val radius = (width / ILLUMINATION_WINDOW_DIVISOR).coerceAtLeast(ILLUMINATION_MIN_RADIUS)
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
            val left = (x - radius).coerceAtLeast(0)
            val right = (x + radius + 1).coerceAtMost(width)
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(height)
            val localSum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val localMean = localSum.toFloat() / ((right - left) * (bottom - top))
            val flattened = FLATTENED_LEVEL + (gray[y * width + x] - localMean) * ILLUMINATION_GAIN
            val level = flattened.roundToInt().coerceIn(0, 255)
            output[y * width + x] = Color.rgb(level, level, level)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    /** Thresholds a flattened image around its card-face level, both polarities. */
    private fun strokeThresholdBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val deviation = abs(Color.red(pixels[i]) - FLATTENED_LEVEL)
            pixels[i] = if (deviation > STROKE_LEVEL_DELTA) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** OpenCV counterpart of [flattenIlluminationBitmap]. */
    private fun openCvFlattenIllumination(source: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        val background = Mat()
        val flattened = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val radius = (source.width / ILLUMINATION_WINDOW_DIVISOR)
                .coerceAtLeast(ILLUMINATION_MIN_RADIUS)
            val window = (radius * 2 + 1).toDouble()
            // Box blur, not Gaussian: it matches the integral-image window the
            // Kotlin fallback uses, so both paths separate strokes the same way.
            Imgproc.blur(gray, background, Size(window, window))
            Core.addWeighted(
                gray,
                ILLUMINATION_GAIN.toDouble(),
                background,
                -ILLUMINATION_GAIN.toDouble(),
                FLATTENED_LEVEL.toDouble(),
                flattened
            )
            return flattened.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            background.release()
            flattened.release()
        }
    }

    /** OpenCV counterpart of [strokeThresholdBitmap]. */
    private fun openCvStrokeThreshold(source: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        val deviation = Mat()
        val binary = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Core.absdiff(gray, Scalar(FLATTENED_LEVEL.toDouble()), deviation)
            Imgproc.threshold(
                deviation,
                binary,
                STROKE_LEVEL_DELTA.toDouble(),
                255.0,
                Imgproc.THRESH_BINARY_INV
            )
            return binary.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            deviation.release()
            binary.release()
        }
    }

    private fun Mat.toBitmap(): Bitmap =
        Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888).also {
            Utils.matToBitmap(this, it)
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

/**
 * Loads the OpenCV native library at most once.
 *
 * Loading is deferred to the first caller, which is always a preprocessing
 * coroutine on `Dispatchers.Default` — never the camera analyzer thread — so a
 * frame is not blocked by the native load. When the load fails (unit tests,
 * an ABI whose `.so` was not packaged) every caller falls back to the
 * pure-Kotlin filters, which is why no variant depends on OpenCV succeeding.
 */
internal object OpenCvSupport {

    private val lock = Any()

    @Volatile
    private var loaded: Boolean? = null

    fun isAvailable(): Boolean {
        loaded?.let { return it }
        return synchronized(lock) {
            loaded ?: runCatching { OpenCVLoader.initLocal() }
                .getOrDefault(false)
                .also { loaded = it }
        }
    }
}
