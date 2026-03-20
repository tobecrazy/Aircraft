package com.young.aircraft.common

import com.young.aircraft.data.GameState
import org.junit.Assert.*
import org.junit.Test

class GameStateManagerTest {

    @Test
    fun `emit does not throw for PLAYING state`() {
        GameStateManager.emit(GameState.PLAYING)
        assertTrue(true) // If no exception, test passes
    }

    @Test
    fun `emit does not throw for PAUSED state`() {
        GameStateManager.emit(GameState.PAUSED)
        assertTrue(true)
    }

    @Test
    fun `emit does not throw for GAME_OVER state`() {
        GameStateManager.emit(GameState.GAME_OVER)
        assertTrue(true)
    }

    @Test
    fun `emit does not throw for LEVEL_COMPLETE state`() {
        GameStateManager.emit(GameState.LEVEL_COMPLETE)
        assertTrue(true)
    }

    @Test
    fun `emit does not throw for GAME_WON state`() {
        GameStateManager.emit(GameState.GAME_WON)
        assertTrue(true)
    }

    @Test
    fun `emit does not throw for LOW_MEMORY state`() {
        GameStateManager.emit(GameState.LOW_MEMORY)
        assertTrue(true)
    }

    @Test
    fun `emit does not throw for multiple sequential emissions`() {
        GameStateManager.emit(GameState.PLAYING)
        GameStateManager.emit(GameState.PAUSED)
        GameStateManager.emit(GameState.GAME_OVER)
        GameStateManager.emit(GameState.LEVEL_COMPLETE)
        GameStateManager.emit(GameState.GAME_WON)
        GameStateManager.emit(GameState.LOW_MEMORY)
        assertTrue(true)
    }

    @Test
    fun `gameState flow is not null`() {
        assertNotNull(GameStateManager.gameState)
    }
}
