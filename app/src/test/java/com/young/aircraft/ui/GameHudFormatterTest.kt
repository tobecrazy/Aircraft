package com.young.aircraft.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GameHudFormatterTest {

    @Test
    fun `remaining seconds clamps to zero when elapsed exceeds level duration`() {
        assertEquals(0, GameHudFormatter.calculateRemainingSeconds(level = 1, elapsedMs = 500_000L))
    }

    @Test
    fun `remaining seconds reflects remaining time budget`() {
        assertEquals(255, GameHudFormatter.calculateRemainingSeconds(level = 3, elapsedMs = 5_000L))
    }

    @Test
    fun `health percent is clamped into valid range`() {
        assertEquals(100, GameHudFormatter.formatHealthPercent(120f))
        assertEquals(0, GameHudFormatter.formatHealthPercent(-4f))
        assertEquals(63, GameHudFormatter.formatHealthPercent(63.9f))
    }

    @Test
    fun `score is derived from total kills`() {
        assertEquals(2_300, GameHudFormatter.calculateScore(23))
        assertEquals(0, GameHudFormatter.calculateScore(-2))
    }
}
