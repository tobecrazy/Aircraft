package com.young.aircraft.common

import com.young.aircraft.data.GameState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GameStateManager {
    private val _gameState = MutableSharedFlow<GameState>(extraBufferCapacity = 1)
    val gameState = _gameState.asSharedFlow()

    fun emit(state: GameState) {
        _gameState.tryEmit(state)
    }
}
