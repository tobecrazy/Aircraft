package com.young.aircraft.gui

import org.junit.Assert.assertEquals
import org.junit.Test

class SupperBannerConfigTest {

    @Test
    fun `transition time is clamped to supported range`() {
        assertEquals(
            SupperBannerConfig.MIN_TRANSITION_TIME_MS,
            SupperBannerConfig.coerceTransitionTimeMillis(100L)
        )
        assertEquals(
            5_000L,
            SupperBannerConfig.coerceTransitionTimeMillis(5_000L)
        )
        assertEquals(
            SupperBannerConfig.MAX_TRANSITION_TIME_MS,
            SupperBannerConfig.coerceTransitionTimeMillis(60_000L)
        )
    }
}
