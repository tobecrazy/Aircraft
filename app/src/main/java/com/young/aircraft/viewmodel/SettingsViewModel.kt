package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.utils.DebugTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val gameDataDao: PlayerGameDataDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(computeState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setTheme(theme: String) {
        repository.setTheme(theme)
        _uiState.value = computeState()
    }

    fun setDifficulty(difficulty: GameDifficulty) {
        repository.setDifficulty(difficulty)
        _uiState.value = computeState()
    }

    fun setBgSoundEnabled(enabled: Boolean) {
        repository.setBackgroundSoundEnabled(enabled)
        _uiState.value = computeState()
    }

    fun setCombatSoundEnabled(enabled: Boolean) {
        repository.setCombatSoundEnabled(enabled)
        _uiState.value = computeState()
    }

    fun setHitShakeEnabled(enabled: Boolean) {
        repository.setHitShakeEffectEnabled(enabled)
        _uiState.value = computeState()
    }

    fun setBgmFormat(value: String) {
        repository.setBgmFormat(value)
        _uiState.value = computeState()
    }

    /** Re-reads preferences so callers coming back from other screens show fresh state. */
    fun refresh() {
        _uiState.value = computeState()
    }

    suspend fun clearCachedGameData() = withContext(Dispatchers.IO) {
        gameDataDao?.deleteAll()
        repository.clearCachedGameData()
    }

    suspend fun getCachedGameDataSizeBytes(): Long = withContext(Dispatchers.IO) {
        repository.getCachedGameDataSizeBytes()
    }

    private fun computeState(): SettingsUiState {
        val bgSound = repository.isBackgroundSoundEnabled()
        val combatSound = repository.isCombatSoundEnabled()
        val hitShake = repository.isHitShakeEffectEnabled()
        val soundOptions = listOf(bgSound, combatSound, hitShake)

        return SettingsUiState(
            theme = repository.getTheme(),
            difficulty = repository.getDifficulty(),
            bgSoundEnabled = bgSound,
            combatSoundEnabled = combatSound,
            hitShakeEnabled = hitShake,
            enabledSoundCount = soundOptions.count { it },
            soundOptionCount = soundOptions.size,
            showDevelopSettings = DebugTools.isEnabled,
            bgmFormat = repository.getBgmFormat()
        )
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val repository = SettingsRepository(context)
        private val gameDataDao = DatabaseProvider.getDatabase(context).playerGameDataDao()

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, gameDataDao) as T
        }
    }
}
