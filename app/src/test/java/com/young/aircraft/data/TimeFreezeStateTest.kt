package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class TimeFreezeStateTest {

    @Test
    fun `default collected is false`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        assertFalse(state.collected)
    }

    @Test
    fun `default collectedByPlayer is false`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        assertFalse(state.collectedByPlayer)
    }

    @Test
    fun `collected can be set to true`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        state.collected = true
        assertTrue(state.collected)
    }

    @Test
    fun `collectedByPlayer can be set to true`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        state.collectedByPlayer = true
        assertTrue(state.collectedByPlayer)
    }

    @Test
    fun `position is stored correctly`() {
        val state = TimeFreezeState(x = 150f, y = 300f, spawnFrame = 10, bitmapIndex = 2)
        assertEquals(150f, state.x, 0.01f)
        assertEquals(300f, state.y, 0.01f)
    }

    @Test
    fun `spawnFrame is stored correctly`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 42, bitmapIndex = 1)
        assertEquals(42, state.spawnFrame)
    }

    @Test
    fun `bitmapIndex is stored correctly`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 2)
        assertEquals(2, state.bitmapIndex)
    }

    @Test
    fun `player collection sets both flags correctly`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        state.collected = true
        state.collectedByPlayer = true
        assertTrue(state.collected)
        assertTrue(state.collectedByPlayer)
    }

    @Test
    fun `enemy collection sets collected true and collectedByPlayer false`() {
        val state = TimeFreezeState(x = 100f, y = 200f, spawnFrame = 0, bitmapIndex = 0)
        state.collected = true
        state.collectedByPlayer = false
        assertTrue(state.collected)
        assertFalse(state.collectedByPlayer)
    }
}
