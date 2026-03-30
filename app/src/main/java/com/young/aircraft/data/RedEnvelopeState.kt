package com.young.aircraft.data

data class RedEnvelopeState(
    val x: Float,
    var y: Float,
    var hitPoints: Int = DEFAULT_HIT_POINTS,
    var destroyedTime: Long = 0L,
    var lastHitTime: Long = 0L
) {
    companion object {
        const val DEFAULT_HIT_POINTS = 3
        const val OPEN_STATE_DURATION_MS = 200L
        const val EXPIRATION_DURATION_MS = OPEN_STATE_DURATION_MS
    }

    fun isDetonated(): Boolean = hitPoints <= 0

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (destroyedTime == 0L) return false
        return nowMs - destroyedTime > EXPIRATION_DURATION_MS
    }

    fun shouldShowOpenState(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (destroyedTime == 0L) return false
        return nowMs - destroyedTime <= OPEN_STATE_DURATION_MS
    }

    fun registerHit(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isDetonated()) return false

        hitPoints = (hitPoints - 1).coerceAtLeast(0)
        lastHitTime = nowMs
        if (!isDetonated()) return false

        destroyedTime = nowMs
        return true
    }
}
