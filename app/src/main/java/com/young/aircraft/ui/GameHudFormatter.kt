package com.young.aircraft.ui

object GameHudFormatter {
    fun calculateRemainingSeconds(level: Int, elapsedMs: Long): Int {
        return ((GameCoreView.getLevelDurationMs(level) - elapsedMs) / 1000L)
            .coerceAtLeast(0L)
            .toInt()
    }

    fun formatHealthPercent(healthPoints: Float): Int {
        return healthPoints.toInt().coerceIn(0, 100)
    }

    fun calculateScore(totalKills: Int): Int {
        return totalKills.coerceAtLeast(0) * 100
    }
}
