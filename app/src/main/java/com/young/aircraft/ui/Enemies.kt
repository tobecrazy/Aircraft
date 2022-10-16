package com.young.aircraft.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.young.aircraft.R
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.utils.ScreenUtils
import kotlin.random.Random

/**
 * Create by Young
 **/
class Enemies(var context: Context, var speed: Float) : DrawBaseObject(context) {
    var enemyX: Float = 0F
    var enemyY: Float = 0F
    val bitmapList = mutableListOf<Bitmap?>()
    val enemiesMap = mutableMapOf<Float, Bitmap?>()

    init {
        enemyY = getRandomTop()
        enemyX = ScreenUtils.getScreenWidth(context).toFloat()
        val originBitmap1 = BitmapUtils.readBitMap(context, R.drawable.enemy_1)
        val originBitmap2 = BitmapUtils.readBitMap(context, R.drawable.enemy_2)
        val originBitmap3 = BitmapUtils.readBitMap(context, R.drawable.enemy_3)
        val originBitmap4 = BitmapUtils.readBitMap(context, R.drawable.enemy_4)
        bitmapList.add(originBitmap1)
        bitmapList.add(originBitmap2)
        bitmapList.add(originBitmap3)
        bitmapList.add(originBitmap4)
        refreshData()
    }

    private fun getRandomEnemyBitmap(): Bitmap? {
        val random = Random(System.nanoTime())
        var index: Int = random.nextInt() % 4
        while (index < 0) {
            index = random.nextInt() % 4
        }
        return bitmapList[index]
    }

    private fun getRandomLeft(): Float {
        val random = Random(System.nanoTime())
        val start = ScreenUtils.dpToPx(
            context, 40.0f
        )
        val end = ScreenUtils.getScreenWidth(context) - ScreenUtils.dpToPx(
            context, 40.0f
        )
        var randomX = enemyX * random.nextFloat()
        while (randomX <= start || randomX >= end) {
            randomX = enemyX * random.nextFloat()
        }
        return randomX
    }

    private fun getRandomTop(): Float {
        val height = ScreenUtils.getScreenWidth(context).toFloat()
        val random = Random(System.nanoTime())
        val topY = ScreenUtils.dpToPx(
            context, 50.0f
        )
        val bottomY = height / 3 - ScreenUtils.dpToPx(
            context, 10.0f
        )
        var randomY = height * random.nextFloat()
        while (randomY <= topY || randomY >= bottomY) {
            randomY = height * random.nextFloat()
        }
        return randomY
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        enemiesMap.forEach { data ->
            data.value?.let { initializeEnemies(it, data.key, canvas) }
        }
    }

    private fun initializeEnemies(originBitmap: Bitmap, left: Float, canvas: Canvas) {
        val enemyBitmap = BitmapUtils.resizeBitmap(
            originBitmap,
            ScreenUtils.dpToPx(context, 48.0f),
            ScreenUtils.dpToPx(context, 48.0f),
            180.0F
        )
        if (enemyBitmap != null) {
            enemyY += 5 * speed
            Log.d("YoungTest", "===> $enemyY")
            if (enemyY > ScreenUtils.getScreenHeight(context).toFloat()) {
                enemyY = 0F
                refreshData()
            }
            canvas.drawBitmap(enemyBitmap, left, enemyY, mPaint)
        }
    }

    private fun refreshData() {
        enemiesMap.clear()
        val random = Random(System.nanoTime())
        var numberOfEnemies: Int = random.nextInt() % 10
        while (numberOfEnemies < 2 || numberOfEnemies > 8) {
            numberOfEnemies = random.nextInt() % 10
        }
        for (i in 1..numberOfEnemies) {
            enemiesMap[getRandomLeft()] = getRandomEnemyBitmap()
        }
    }

    override fun updateGame() {

    }
}