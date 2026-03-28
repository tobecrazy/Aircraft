package com.young.aircraft.ui

import org.junit.Assert.*
import org.junit.Test

class GameCoreViewFormulaTest {

    // --- getLevelDurationMs ---

    @Test
    fun `level 1 duration is 300 seconds`() {
        assertEquals(300_000L, GameCoreView.getLevelDurationMs(1))
    }

    @Test
    fun `level 5 duration is 220 seconds`() {
        assertEquals(220_000L, GameCoreView.getLevelDurationMs(5))
    }

    @Test
    fun `level 10 duration is 120 seconds`() {
        assertEquals(120_000L, GameCoreView.getLevelDurationMs(10))
    }

    @Test
    fun `level duration decreases by 20s per level`() {
        for (level in 1..9) {
            val diff = GameCoreView.getLevelDurationMs(level) - GameCoreView.getLevelDurationMs(level + 1)
            assertEquals(20_000L, diff)
        }
    }

    // --- getRequiredKills ---

    @Test
    fun `level 1 requires 100 kills`() {
        assertEquals(100, GameCoreView.getRequiredKills(1))
    }

    @Test
    fun `level 5 requires 140 kills`() {
        assertEquals(140, GameCoreView.getRequiredKills(5))
    }

    @Test
    fun `level 10 requires 190 kills`() {
        assertEquals(190, GameCoreView.getRequiredKills(10))
    }

    @Test
    fun `required kills increases by 10 per level`() {
        for (level in 1..9) {
            val diff = GameCoreView.getRequiredKills(level + 1) - GameCoreView.getRequiredKills(level)
            assertEquals(10, diff)
        }
    }

    // --- Constants ---

    @Test
    fun `FPS is 30`() {
        assertEquals(30, GameCoreView.FPS)
    }

    @Test
    fun `MAX_LEVEL is 10`() {
        assertEquals(10, GameCoreView.MAX_LEVEL)
    }

    @Test
    fun `frame sleep time clamps to zero when frame overruns`() {
        assertEquals(0L, GameCoreView.calculateFrameSleepTimeMs(33L, 45L))
    }

    @Test
    fun `frame sleep time uses remaining budget when frame is under target`() {
        assertEquals(13L, GameCoreView.calculateFrameSleepTimeMs(33L, 20L))
    }

    @Test
    fun `dropped frames stays zero when frame fits budget`() {
        assertEquals(0, GameCoreView.calculateDroppedFrames(33L, 33L))
    }

    @Test
    fun `dropped frames counts overruns in target frame chunks`() {
        assertEquals(2, GameCoreView.calculateDroppedFrames(33L, 100L))
    }
}
