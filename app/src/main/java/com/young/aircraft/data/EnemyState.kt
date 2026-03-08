package com.young.aircraft.data

import android.graphics.Bitmap

/**
 * @author Young
 */
data class EnemyState(
    val x: Float,
    var y: Float,
    val bitmap: Bitmap?,
    var health: Float,
    var destroyedTime: Long = 0L,
    val bullets: MutableList<Float> = mutableListOf()
) {
    fun isDestroyed(): Boolean = health <= 0

    fun isExpired(): Boolean {
        if (destroyedTime == 0L) return false
        return System.currentTimeMillis() - destroyedTime >= 1000L
    }
}
