package com.young.aircraft.data

/**
 * Create by Young
 * Default aircraft data class
 **/
data class Aircraft(
    val name: String,
    var health_points: Float = 100.0f,
    var lethality: Float = 20.0f,
    val icon: Int = 0
) {
    companion object {
        const val BULLET_DAMAGE = 20.0f
    }

    fun hit() {
        health_points -= BULLET_DAMAGE
    }

    fun isAlive(): Boolean {
        return health_points > 0
    }
}
