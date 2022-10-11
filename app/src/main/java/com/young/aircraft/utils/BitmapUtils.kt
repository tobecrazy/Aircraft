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
}