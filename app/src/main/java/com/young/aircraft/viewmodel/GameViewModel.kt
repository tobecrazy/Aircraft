package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameMode
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository

class GameViewModel(
    private val dao: PlayerGameDataDao,
    private val playerId: String,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun calculateScore(totalKills: Int): Long = totalKills.toLong() * 100

    fun shouldAutoSaveOnExit(level: Int, totalKills: Int): Boolean {
        return level > 1 || totalKills > 0
    }

    fun getDifficulty(): GameDifficulty = settingsRepository.getDifficulty()

    suspend fun saveGameData(
        level: Int,
        totalKills: Int,
        puzzleScore: Long,
        puzzleLevel: Int,
        gameMode: GameMode,
        jetPlaneResId: Int,
        jetPlaneIndex: Int,
        playerName: String? = null
    ) {
        val score = calculateScore(totalKills)
        val difficulty = settingsRepository.getDifficulty().persistedValue
        val existingRecord = dao.getByPlayerId(playerId).firstOrNull()
        val persistedPlayerName = playerName ?: existingRecord?.playerName
        dao.deleteByPlayerId(playerId)
        dao.insert(
            PlayerGameData(
                playerId = playerId,
                playerName = persistedPlayerName,
                level = level,
                airBattleLevel = level,
                puzzleLevel = puzzleLevel,
                gameMode = gameMode.name,
                score = score,
                puzzleScore = puzzleScore,
                totalKills = totalKills,
                jetPlaneRes = jetPlaneResId,
                jetPlaneIndex = jetPlaneIndex,
                difficulty = difficulty
            )
        )
    }

    suspend fun saveAirBattleData(
        level: Int,
        totalKills: Int,
        jetPlaneResId: Int,
        jetPlaneIndex: Int,
        playerName: String? = null
    ) {
        val existingRecord = dao.getByPlayerId(playerId).firstOrNull()
        val preservedPuzzleScore = existingRecord?.puzzleScore ?: 0L
        val preservedPuzzleLevel = existingRecord?.puzzleLevel ?: 1
        saveGameData(
            level = level,
            totalKills = totalKills,
            puzzleScore = preservedPuzzleScore,
            puzzleLevel = preservedPuzzleLevel,
            gameMode = GameMode.AIR_BATTLE,
            jetPlaneResId = jetPlaneResId,
            jetPlaneIndex = jetPlaneIndex,
            playerName = playerName
        )
    }

    suspend fun deletePlayerData() {
        dao.deleteByPlayerId(playerId)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val settingsRepository = SettingsRepository(context)
        private val dao = DatabaseProvider.getDatabase(context).playerGameDataDao()
        private val playerId = settingsRepository.getOrCreateInstallId()

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameViewModel(dao, playerId, settingsRepository) as T
        }
    }
}
