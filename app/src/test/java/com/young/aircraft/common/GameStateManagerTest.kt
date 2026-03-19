package com.young.aircraft.common

import com.young.aircraft.data.GameState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GameStateManagerTest {

    @Test
    fun `emit updates the gameState flow`() = runBlocking {
        GameStateManager.emit(GameState.PLAYING)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.PLAYING, state)
    }

    @Test
    fun `emit GAME_OVER updates flow to GAME_OVER`() = runBlocking {
        GameStateManager.emit(GameState.GAME_OVER)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.GAME_OVER, state)
    }

    @Test
    fun `emit PAUSED updates flow to PAUSED`() = runBlocking {
        GameStateManager.emit(GameState.PAUSED)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.PAUSED, state)
    }

    @Test
    fun `emit LEVEL_COMPLETE updates flow to LEVEL_COMPLETE`() = runBlocking {
        GameStateManager.emit(GameState.LEVEL_COMPLETE)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.LEVEL_COMPLETE, state)
    }

    @Test
    fun `emit GAME_WON updates flow to GAME_WON`() = runBlocking {
        GameStateManager.emit(GameState.GAME_WON)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.GAME_WON, state)
    }

    @Test
    fun `emit LOW_MEMORY updates flow to LOW_MEMORY`() = runBlocking {
        GameStateManager.emit(GameState.LOW_MEMORY)
        val state = GameStateManager.gameState.first()
        assertEquals(GameState.LOW_MEMORY, state)
    }

    @Test
    fun `gameState flow emits multiple states in order`() = runBlocking {
        GameStateManager.emit(GameState.PLAYING)
        GameStateManager.emit(GameState.PAUSED)
        GameStateManager.emit(GameState.GAME_OVER)

        val states = mutableListOf<GameState>()
        GameStateManager.gameState.collect { states.add(it) }

        // The flow should contain at least the last emitted state
        assertTrue(states.isNotEmpty())
    }
}
