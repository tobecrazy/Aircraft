package com.young.aircraft.data

data class RedEnvelopeState(
    val x: Float,
    var y: Float,
    var hitPoints: Int = 3,
    var destroyedTime: Long = 0L,
    var lastHitTime: Long = 0L
) {
    fun isDetonated(): Boolean = hitPoints <= 0

    fun isExpired(): Boolean {
        if (destroyedTime == 0L) return false
        return System.currentTimeMillis() - destroyedTime >= 500L
    }
}
