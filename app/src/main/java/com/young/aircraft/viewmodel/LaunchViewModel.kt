package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.data.GameMode
import com.young.aircraft.gui.SavedGameInfo
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.launch

class LaunchViewModel(
    private val dao: PlayerGameDataDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun checkForSavedGame(): SavedGameInfo? {
        val playerId = settingsRepository.getOrCreateInstallId()
        val savedData = dao.getByPlayerId(playerId)
        val data = savedData.firstOrNull()
        if (data != null && shouldOfferSavedGame(data)) {
            val savedJetIndex = if (data.jetPlaneIndex in Aircraft.JET_PLANES.indices) {
                data.jetPlaneIndex
            } else {
                val foundIndex = Aircraft.JET_PLANES.indexOf(data.jetPlaneRes)
                if (foundIndex != -1) foundIndex else 0
            }
            val savedJetRes = Aircraft.JET_PLANES[savedJetIndex]
            return SavedGameInfo(
                level = data.airBattleLevel,
                jetIndex = savedJetIndex,
                jetRes = savedJetRes,
                totalKills = data.totalKills
            )
        }
        return null
    }

    fun deleteSavedGame() {
        val playerId = settingsRepository.getOrCreateInstallId()
        viewModelScope.launch {
            dao.deleteByPlayerId(playerId)
        }
    }

    private fun shouldOfferSavedGame(data: PlayerGameData): Boolean {
        return (data.level > 1 || data.score > 0L) && data.gameMode == GameMode.AIR_BATTLE.name
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val dao = DatabaseProvider.getDatabase(context).playerGameDataDao()
        private val settingsRepository = SettingsRepository(context)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LaunchViewModel(dao, settingsRepository) as T
        }
    }
}
