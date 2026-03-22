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
    private const val CACHE_DIVISOR = 32
    private val decodedBitmapCache = object : LruCache<Int, Bitmap>(defaultCacheSizeInKb()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.cacheSizeInKb()
    }
    private val transformedBitmapCache =
        object : LruCache<TransformedBitmapKey, Bitmap>(defaultCacheSizeInKb()) {
            override fun sizeOf(key: TransformedBitmapKey, value: Bitmap): Int = value.cacheSizeInKb()
        }
    private val bitmapIds = WeakHashMap<Bitmap, Int>()
    private var nextBitmapId = 1

    private data class TransformedBitmapKey(
        val sourceId: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val targetWidth: Int,
        val targetHeight: Int,
        val rotationBits: Int
    )

    @Synchronized
    fun clearCaches() {
        decodedBitmapCache.evictAll()
        transformedBitmapCache.evictAll()
        bitmapIds.clear()
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
        return bitmap
    }


    fun getScaleMap(bitmap: Bitmap): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return bitmap
        }
        val matrix = Matrix()
        matrix.postScale(1F, -1F)
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: IllegalArgumentException) {
            bitmap
        } catch (_: RuntimeException) {
            bitmap
        }
    }

    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int): Bitmap? {
        return resizeBitmapInternal(bitmap, width, height, 0f)
    }

    @Synchronized
    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int, degrees: Float): Bitmap? {
        return resizeBitmapInternal(bitmap, width, height, degrees)
    }

    @Synchronized
    private fun resizeBitmapInternal(bitmap: Bitmap?, width: Int, height: Int, degrees: Float): Bitmap? {
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
        if (normalizedDegrees == 0f && sourceWidth == width && sourceHeight == height) {
            return bitmap
        }

        val cacheKey = TransformedBitmapKey(
            sourceId = bitmapId(bitmap),
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = width,
            targetHeight = height,
            rotationBits = normalizedDegrees.toRawBits()
        )

        transformedBitmapCache.get(cacheKey)?.takeUnless { it.isRecycled }?.let { return it }

        val transformed = createTransformedBitmap(bitmap, width, height, sourceWidth, sourceHeight, normalizedDegrees)
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
        degrees: Float
    ): Bitmap? {
        return try {
            if (degrees == 0f) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                val scaleWidth = width.toFloat() / sourceWidth
                val scaleHeight = height.toFloat() / sourceHeight
                val matrix = Matrix().apply {
                    if (sourceWidth != width || sourceHeight != height) {
                        postScale(scaleWidth, scaleHeight)
                    }
                    postRotate(degrees)
                }
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
