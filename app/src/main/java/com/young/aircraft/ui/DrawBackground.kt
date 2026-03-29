package com.young.aircraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.math.floor

/**
 * Create by Young
 **/
class DrawBackground(var context: Context, var speed: Float) : DrawBaseObject(context) {

    companion object {
        private const val BASE_SCROLL_SPEED = 10f
        val BACKGROUNDS = intArrayOf(R.drawable.background, R.drawable.background_1, R.drawable.background_2)
    }

    internal data class BackgroundTile(
        val top: Float,
        val bottom: Float,
        val mirrored: Boolean
    )

    // background Top/Bottom
    var mTopY: Float = 0F
    var mBottomY: Float = 0F
    private var backgroundResId: Int = BACKGROUNDS.random()
    private var primaryBitmap: Bitmap? = null
    private var mirroredBitmap: Bitmap? = null
    private var renderedWidth: Int = 0
    private var renderedHeight: Int = 0
    private var scrollOffset: Float = 0F
    private var loopHeight: Float = 0F

    init {
        loadBitmap(
            ScreenUtils.getScreenWidth(context),
            ScreenUtils.getScreenHeight(context)
        )
    }

    private fun loadBitmap(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            clearBitmaps()
            return
        }

        val originalBitmap = BitmapUtils.readBitMap(context, backgroundResId)
        val resizedBitmap = BitmapUtils.resizeBitmap(originalBitmap, width, height)

        if (resizedBitmap == null) {
            clearBitmaps()
            return
        }

        // Use the optimized and cached mirrored bitmap from BitmapUtils
        val flippedBitmap = BitmapUtils.getScaleMap(resizedBitmap)

        primaryBitmap = resizedBitmap
        mirroredBitmap = flippedBitmap
        renderedWidth = resizedBitmap.width
        renderedHeight = resizedBitmap.height
        loopHeight = renderedHeight * 2f
        resetScroll()
    }

    private fun clearBitmaps() {
        primaryBitmap = null
        mirroredBitmap = null
        renderedWidth = 0
        renderedHeight = 0
        loopHeight = 0f
        scrollOffset = 0f
        mTopY = 0f
        mBottomY = 0f
    }

    private fun resetScroll() {
        scrollOffset = 0f
        mTopY = -renderedHeight.toFloat()
        mBottomY = 0f
    }

    private fun ensureBitmapForCanvas(canvas: Canvas) {
        if (primaryBitmap == null || mirroredBitmap == null ||
            renderedWidth != canvas.width || renderedHeight != canvas.height
        ) {
            loadBitmap(canvas.width, canvas.height)
        }
    }

    private fun normalizeScrollOffset(offset: Float): Float {
        if (loopHeight <= 0f) {
            return 0f
        }
        val normalized = offset % loopHeight
        return if (normalized < 0f) normalized + loopHeight else normalized
    }

    internal fun calculateVisibleTiles(viewportHeight: Float = renderedHeight.toFloat()): List<BackgroundTile> {
        if (renderedHeight <= 0 || viewportHeight <= 0f || loopHeight <= 0f) {
            return emptyList()
        }

        val tileHeight = renderedHeight.toFloat()
        val normalizedOffset = normalizeScrollOffset(scrollOffset)
        var tileIndex = floor(normalizedOffset / tileHeight).toInt()
        var drawY = -(normalizedOffset % tileHeight)
        val visibleTiles = mutableListOf<BackgroundTile>()

        while (drawY < viewportHeight) {
            val bottom = drawY + tileHeight
            visibleTiles.add(
                BackgroundTile(
                    top = drawY,
                    bottom = bottom,
                    mirrored = tileIndex % 2 != 0
                )
            )
            drawY = bottom
            tileIndex++
        }

        return visibleTiles
    }

    private fun updateLegacyOffsets(visibleTiles: List<BackgroundTile>) {
        val firstTile = visibleTiles.getOrNull(0)
        val secondTile = visibleTiles.getOrNull(1)
        val tileHeight = renderedHeight.toFloat()

        mTopY = firstTile?.top ?: -tileHeight
        mBottomY = secondTile?.top ?: (mTopY + tileHeight)
    }

    fun randomizeBackground() {
        if (BACKGROUNDS.size > 1) {
            var nextResId = backgroundResId
            while (nextResId == backgroundResId) {
                nextResId = BACKGROUNDS.random()
            }
            backgroundResId = nextResId
        }

        loadBitmap(
            renderedWidth.takeIf { it > 0 } ?: ScreenUtils.getScreenWidth(context),
            renderedHeight.takeIf { it > 0 } ?: ScreenUtils.getScreenHeight(context)
        )
    }

    override fun onDraw(canvas: Canvas) {
        ensureBitmapForCanvas(canvas)

        val baseBitmap = primaryBitmap ?: return
        val flippedBitmap = mirroredBitmap ?: return

        canvas.density = baseBitmap.density
        scrollOffset = normalizeScrollOffset(scrollOffset + BASE_SCROLL_SPEED * speed)

        val visibleTiles = calculateVisibleTiles(canvas.height.toFloat())
        if (visibleTiles.isEmpty()) {
            return
        }

        updateLegacyOffsets(visibleTiles)

        for (tile in visibleTiles) {
            val bitmap = if (tile.mirrored) flippedBitmap else baseBitmap
            canvas.drawBitmap(bitmap, 0F, tile.top, mPaint)
        }
    }

    override fun updateGame() {

    }

    override fun getEnemyBounds(x: Float, y: Float, bitmap: Bitmap): RectF {
        return RectF()
    }
}
