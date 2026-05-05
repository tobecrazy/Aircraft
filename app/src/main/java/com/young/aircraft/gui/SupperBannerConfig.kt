package com.young.aircraft.gui

object SupperBannerConfig {
    const val DEFAULT_TRANSITION_TIME_MS = 3_000L
    const val MIN_TRANSITION_TIME_MS = 800L
    const val MAX_TRANSITION_TIME_MS = 15_000L

    fun coerceTransitionTimeMillis(value: Long): Long =
        value.coerceIn(MIN_TRANSITION_TIME_MS, MAX_TRANSITION_TIME_MS)
}
