package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class PlayerGameDataTest {

    @Test
    fun `default values are set correctly`() {
        val data = PlayerGameData(playerId = "test123", level = 1, score = 0)
        assertEquals("test123", data.playerId)
        assertEquals(1, data.level)
        assertEquals(0L, data.score)
        assertEquals(0, data.jetPlaneRes)
        assertEquals(0, data.jetPlaneIndex)
        assertEquals("1.0", data.difficulty)
        assertTrue(data.timestamp > 0)
    }

    @Test
    fun `all fields can be set via constructor`() {
        val timestamp = System.currentTimeMillis()
        val data = PlayerGameData(
            id = 1L,
            playerId = "player1",
            level = 5,
            score = 10000L,
            jetPlaneRes = 123,
            jetPlaneIndex = 2,
            difficulty = "0.8",
            timestamp = timestamp
        )
        assertEquals(1L, data.id)
        assertEquals("player1", data.playerId)
        assertEquals(5, data.level)
        assertEquals(10000L, data.score)
        assertEquals(123, data.jetPlaneRes)
        assertEquals(2, data.jetPlaneIndex)
        assertEquals("0.8", data.difficulty)
        assertEquals(timestamp, data.timestamp)
    }

    @Test
    fun `copy works correctly`() {
        val original = PlayerGameData(playerId = "test", level = 3, score = 500L)
        val copy = original.copy(level = 5)
        assertEquals("test", copy.playerId)
        assertEquals(5, copy.level)
        assertEquals(500L, copy.score)
    }

    @Test
    fun `data class equality works`() {
        val data1 = PlayerGameData(playerId = "test", level = 1, score = 100L)
        val data2 = PlayerGameData(playerId = "test", level = 1, score = 100L)
        assertEquals(data1, data2)
    }

    @Test
    fun `different data classes are not equal`() {
        val data1 = PlayerGameData(playerId = "test1", level = 1, score = 100L)
        val data2 = PlayerGameData(playerId = "test2", level = 1, score = 100L)
        assertNotEquals(data1, data2)
    }
}
