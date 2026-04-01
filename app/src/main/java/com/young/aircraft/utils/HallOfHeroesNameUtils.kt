package com.young.aircraft.utils

import com.young.aircraft.data.PlayerGameData

object HallOfHeroesNameUtils {
    fun resolveSubmittedName(rawName: CharSequence?, anonymousLabel: String): String {
        val trimmedName = rawName?.toString()?.trim()
        return if (trimmedName.isNullOrEmpty()) {
            anonymousLabel
        } else {
            trimmedName
        }
    }

    fun getDisplayName(record: PlayerGameData): String {
        val trimmedName = record.playerName?.trim()
        return if (!trimmedName.isNullOrEmpty()) {
            trimmedName
        } else {
            truncatePlayerId(record.playerId)
        }
    }

    fun truncatePlayerId(playerId: String, maxLength: Int = 6): String {
        return if (playerId.length > maxLength) {
            playerId.take(maxLength) + "\u2026"
        } else {
            playerId
        }
    }
}
