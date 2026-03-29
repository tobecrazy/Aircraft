package com.young.aircraft.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import java.util.WeakHashMap


/**
 * Create by Young
 **/
object BitmapUtils {
    // Increased cache size: use 1/8 of max memory for each cache instead of 1/32
    private const val CACHE_DIVISOR = 8
    private val decodedBitmapCache = object : LruCache<Int, Bitmap>(defaultCacheSizeInKb()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.cacheSizeInKb()
    }
    private val transformedBitmapCache =
        object : LruCache<TransformedBitmapKey, Bitmap>(defaultCacheSizeInKb()) {
            override fun sizeOf(key: TransformedBitmapKey, value: Bitmap): Int = value.cacheSizeInKb()
        }
    private val bitmapIds = WeakHashMap<Bitmap, Int>()
    private val bitmapResIds = WeakHashMap<Bitmap, Int>()
    private var nextBitmapId = 1

    private data class TransformedBitmapKey(
        val sourceId: Int, // Can be resId or internal bitmapId
        val sourceWidth: Int,
        val sourceHeight: Int,
        val targetWidth: Int,
        val targetHeight: Int,
        val rotationBits: Int,
        val mirrored: Boolean
    )

    @Synchronized
    fun clearCaches() {
        decodedBitmapCache.evictAll()
        transformedBitmapCache.evictAll()
        bitmapIds.clear()
        bitmapResIds.clear()
        nextBitmapId = 1
    }

    @Synchronized
    fun readBitMap(context: Context, resId: Int): Bitmap? {
        if (resId == 0) {
            return null
        }

        decodedBitmapCache.get(resId)?.takeUnless { it.isRecycled }?.let { return it }

        val bitmap = decodeResourceSafely(context.applicationContext.resources, resId) ?: return null
        decodedBitmapCache.put(resId, bitmap)
        synchronized(bitmapResIds) {
            bitmapResIds[bitmap] = resId
        }
        return bitmap
    }


    fun getScaleMap(bitmap: Bitmap): Bitmap {
        return resizeBitmapInternal(bitmap, bitmap.width, bitmap.height, 0f, true) ?: bitmap
    }

    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int): Bitmap? {
        return resizeBitmapInternal(bitmap, width, height, 0f, false)
    }

    @Synchronized
    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int, degrees: Float): Bitmap? {
        return resizeBitmapInternal(bitmap, width, height, degrees, false)
    }

    @Synchronized
    private fun resizeBitmapInternal(
        bitmap: Bitmap?,
        width: Int,
        height: Int,
        degrees: Float,
        mirrored: Boolean
    ): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) {
            return null
        }

        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null
        }

        if (width <= 0 || height <= 0) {
            return bitmap
        }

        val normalizedDegrees = normalizeDegrees(degrees)
        // If no transformation needed, return original (but mirrored still needs transformation)
        if (!mirrored && normalizedDegrees == 0f && sourceWidth == width && sourceHeight == height) {
            return bitmap
        }

        // Use resId as sourceId if available, otherwise fallback to internal bitmapId.
        // This ensures that even if the raw bitmap is evicted and re-decoded, the cache key remains stable.
        val sourceId = synchronized(bitmapResIds) { bitmapResIds[bitmap] } ?: bitmapId(bitmap)

        val cacheKey = TransformedBitmapKey(
            sourceId = sourceId,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = width,
            targetHeight = height,
            rotationBits = normalizedDegrees.toRawBits(),
            mirrored = mirrored
        )

        transformedBitmapCache.get(cacheKey)?.takeUnless { it.isRecycled }?.let { return it }

        val transformed = createTransformedBitmap(bitmap, width, height, sourceWidth, sourceHeight, normalizedDegrees, mirrored)
            ?: return bitmap

        transformed.density = bitmap.density
        transformedBitmapCache.put(cacheKey, transformed)
        return transformed
    }

    private fun createTransformedBitmap(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        degrees: Float,
        mirrored: Boolean
    ): Bitmap? {
        return try {
            val matrix = Matrix().apply {
                val scaleWidth = width.toFloat() / sourceWidth
                val scaleHeight = height.toFloat() / sourceHeight
                if (sourceWidth != width || sourceHeight != height) {
                    postScale(scaleWidth, scaleHeight)
                }
                if (mirrored) {
                    postScale(1F, -1F)
                }
                if (degrees != 0f) {
                    postRotate(degrees)
                }
            }
            
            if (matrix.isIdentity) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, sourceWidth, sourceHeight, matrix, true)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun decodeResourceSafely(resources: Resources, resId: Int): Bitmap? {
        return try {
            BitmapFactory.decodeResource(
                resources,
                resId,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inMutable = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun bitmapId(bitmap: Bitmap): Int {
        return synchronized(bitmapIds) {
            bitmapIds[bitmap] ?: nextBitmapId++.also { bitmapIds[bitmap] = it }
        }
    }

    private fun normalizeDegrees(degrees: Float): Float {
        val normalized = degrees % 360f
        return when {
            normalized == 0f -> 0f
            normalized < 0f -> normalized + 360f
            else -> normalized
        }
    }

    private fun Bitmap.cacheSizeInKb(): Int = (allocationByteCount / 1024).coerceAtLeast(1)

    private fun defaultCacheSizeInKb(): Int {
        val maxMemoryInKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        return (maxMemoryInKb / CACHE_DIVISOR).coerceAtLeast(1024)
    }

}
