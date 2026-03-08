package com.young.aircraft.data

/**
 * Create by Young
 * Default aircraft data class
 **/
data class Aircraft(
    val name: String,
    var health_points: Float = 100.0f,
    var lethality: Float,
    val icon: Int
) {
    companion object {
        fun isAlive(aircraft: Aircraft): Boolean {
            return aircraft.health_points > 0
        }


    }
}
