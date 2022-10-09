package com.young.aircraft.data

/**
 * Create by Young
 **/
data class Aircraft(
    val name: String,
    var health_points: Float,
    var lethality: Float,
    val icon: Int
) {
    companion object {
        fun isAlive(aircraft: Aircraft): Boolean {
            return aircraft.health_points > 0
        }


    }
}
