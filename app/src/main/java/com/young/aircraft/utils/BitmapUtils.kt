package com.young.aircraft.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix


/**
 * Create by Young
 **/
object BitmapUtils {

    fun readBitMap(context: Context, resId: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inScaled = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            .copy(Bitmap.Config.ARGB_8888, true);
        return bitmap
    }


    fun getScaleMap(bitmap: Bitmap): Bitmap? {
        val matrix = Matrix()
        matrix.postScale(1F, -1F)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int): Bitmap? {
        return bitmap?.let {
            val oldWidth = it.width
            val oldHeight = it.height
            val scaleWight: Float = width.toFloat() / oldWidth
            val scaleHeight: Float = height.toFloat() / oldHeight
            val matrix = Matrix()
            matrix.reset()
            matrix.postScale(scaleWight, scaleHeight);
            val res = Bitmap.createBitmap(bitmap, 0, 0, oldWidth, oldHeight, matrix, true);
            res
        }
    }

    @Synchronized
    fun resizeBitmap(bitmap: Bitmap?, width: Int, height: Int, degrees: Float): Bitmap? {
        return bitmap?.let {
            val oldWidth = it.width
            val oldHeight = it.height
            val scaleWight: Float = width.toFloat() / oldWidth
            val scaleHeight: Float = height.toFloat() / oldHeight
            val matrix = Matrix()
            matrix.reset()
            //旋转角度
            matrix.setRotate(degrees);
            matrix.postScale(scaleWight, scaleHeight);
            val res = Bitmap.createBitmap(bitmap, 0, 0, oldWidth, oldHeight, matrix, true);
            res
        }
    }


}