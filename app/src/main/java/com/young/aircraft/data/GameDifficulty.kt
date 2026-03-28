package com.young.aircraft.data

enum class GameDifficulty(
    val persistedValue: String,
    val fireRateMultiplier: Float
) {
    EASY(persistedValue = "1.2", fireRateMultiplier = 1.2f),
    NORMAL(persistedValue = "1.0", fireRateMultiplier = 1.0f),
    HARD(persistedValue = "0.8", fireRateMultiplier = 0.8f);

    companion object {
        fun fromPersistedValue(value: String?): GameDifficulty {
            return values().firstOrNull { it.persistedValue == value } ?: NORMAL
        }
    }
}
