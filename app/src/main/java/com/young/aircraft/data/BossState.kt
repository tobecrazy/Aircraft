package com.young.aircraft.data

data class BossBomb(
    var x: Float,
    var y: Float,
    val bitmapIndex: Int
)

data class BossState(
    var x: Float,
    var y: Float,
    var hitPoints: Float,
    val maxHitPoints: Float,
    var destroyedTime: Long = 0L,
    var lastHitTime: Long = 0L,
    val bitmapIndex: Int,
    val bombs: MutableList<BossBomb> = mutableListOf()
) {
    fun isDestroyed(): Boolean = hitPoints <= 0

    fun isExpired(): Boolean {
        if (destroyedTime == 0L) return false
        return System.currentTimeMillis() - destroyedTime >= 3500L
    }
}
