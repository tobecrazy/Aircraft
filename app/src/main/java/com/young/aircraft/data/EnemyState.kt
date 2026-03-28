package com.young.aircraft.data

import android.graphics.Bitmap

/**
 * @author Young
 */

data class EnemyBullet(var y: Float, val originY: Float)

data class EnemyState(
    val x: Float,
    var y: Float,
    val bitmap: Bitmap?,
    val bitmapIndex: Int = -1,
    var health: Float,
    var destroyedTime: Long = 0L,
    val bullets: MutableList<EnemyBullet> = mutableListOf()
) {
    fun isDestroyed(): Boolean = health <= 0

    fun isExpired(): Boolean {
        if (destroyedTime == 0L) return false
        return System.currentTimeMillis() - destroyedTime >= 1000L
    }
}
