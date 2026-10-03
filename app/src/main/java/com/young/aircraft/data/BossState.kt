package com.young.aircraft.data

data class BossBomb(
    var x: Float,
    var y: Float,
    val bitmapIndex: Int,
    /** Horizontal velocity in px/frame; 0 = straight down. */
    val vx: Float = 0f,
    /** Spread shots render as a red orb instead of a missile sprite. */
    val isSpreadShot: Boolean = false
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

    /** Below the given fraction of max HP the boss switches to spread shot. */
    fun isBelowHpRatio(ratio: Float): Boolean = hitPoints < maxHitPoints * ratio

    fun isExpired(): Boolean {
        if (destroyedTime == 0L) return false
        return System.currentTimeMillis() - destroyedTime >= 3500L
    }
}
