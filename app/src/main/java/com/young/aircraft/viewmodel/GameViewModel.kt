package com.young.aircraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao

class GameViewModel(
    private val dao: PlayerGameDataDao,
    private val playerId: String
) : ViewModel() {

    fun calculateScore(totalKills: Int): Long = totalKills.toLong() * 100

    fun shouldAutoSaveOnExit(level: Int, totalKills: Int): Boolean {
        return level > 1 || totalKills > 0
    }

    suspend fun saveGameData(
        level: Int,
        totalKills: Int,
        jetPlaneResId: Int,
        jetPlaneIndex: Int,
        difficulty: String,
        playerName: String? = null
    ) {
        val score = calculateScore(totalKills)
        val existingRecord = dao.getByPlayerId(playerId).firstOrNull()
        val persistedPlayerName = playerName ?: existingRecord?.playerName
        dao.deleteByPlayerId(playerId)
        dao.insert(
            PlayerGameData(
                playerId = playerId,
                playerName = persistedPlayerName,
                level = level,
                score = score,
                jetPlaneRes = jetPlaneResId,
                jetPlaneIndex = jetPlaneIndex,
                difficulty = difficulty
            )
        )
    }

    suspend fun deletePlayerData() {
        dao.deleteByPlayerId(playerId)
    }

    class Factory(
        private val dao: PlayerGameDataDao,
        private val playerId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameViewModel(dao, playerId) as T
        }
    }
}
