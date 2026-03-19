package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class GameStateTest {

    @Test
    fun `GameState enum has all expected values`() {
        val values = GameState.entries.toTypedArray()
        assertEquals(6, values.size)
        assertTrue(values.contains(GameState.PLAYING))
        assertTrue(values.contains(GameState.PAUSED))
        assertTrue(values.contains(GameState.GAME_OVER))
        assertTrue(values.contains(GameState.LEVEL_COMPLETE))
        assertTrue(values.contains(GameState.GAME_WON))
        assertTrue(values.contains(GameState.LOW_MEMORY))
    }

    @Test
    fun `GameState PLAYING has ordinal 0`() {
        assertEquals(0, GameState.PLAYING.ordinal)
    }

    @Test
    fun `GameState PAUSED has ordinal 1`() {
        assertEquals(1, GameState.PAUSED.ordinal)
    }

    @Test
    fun `GameState GAME_OVER has ordinal 2`() {
        assertEquals(2, GameState.GAME_OVER.ordinal)
    }

    @Test
    fun `GameState LEVEL_COMPLETE has ordinal 3`() {
        assertEquals(3, GameState.LEVEL_COMPLETE.ordinal)
    }

    @Test
    fun `GameState GAME_WON has ordinal 4`() {
        assertEquals(4, GameState.GAME_WON.ordinal)
    }

    @Test
    fun `GameState LOW_MEMORY has ordinal 5`() {
        assertEquals(5, GameState.LOW_MEMORY.ordinal)
    }

    @Test
    fun `GameState can be compared by ordinal`() {
        assertTrue(GameState.PLAYING.ordinal < GameState.PAUSED.ordinal)
        assertTrue(GameState.PAUSED.ordinal < GameState.GAME_OVER.ordinal)
    }

    @Test
    fun `GameState from name works`() {
        assertEquals(GameState.PLAYING, GameState.valueOf("PLAYING"))
        assertEquals(GameState.GAME_OVER, GameState.valueOf("GAME_OVER"))
        assertEquals(GameState.LOW_MEMORY, GameState.valueOf("LOW_MEMORY"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `GameState valueOf throws for invalid name`() {
        GameState.valueOf("INVALID")
    }
}
