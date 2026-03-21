package com.young.aircraft.data

/**
 * Create by Young
 * Default aircraft data class
 **/
data class PlayerAircraft(
    val name: String,
    var health_points: Float = 100.0f,
    var lethality: Float = 20.0f,
    val icon: Int = 0
) {
    companion object {
        const val BULLET_DAMAGE = 20.0f
        const val MAX_HP = 100.0f
    }

    fun restoreHealth() {
        health_points = MAX_HP
    }

    fun isFullHealth(): Boolean = health_points >= MAX_HP

    fun hit() {
        health_points -= BULLET_DAMAGE
    }

    fun isAlive(): Boolean {
        return health_points > 0
    }
}
