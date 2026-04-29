package com.young.aircraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.BuildConfig
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.providers.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(computeState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    private fun computeState(): SettingsUiState {
        val bgSound = repository.isBackgroundSoundEnabled()
        val combatSound = repository.isCombatSoundEnabled()
        val hitShake = repository.isHitShakeEffectEnabled()
        val enabledCount = listOf(bgSound, combatSound, hitShake).count { it }

        return SettingsUiState(
            difficulty = repository.getDifficulty(),
            bgSoundEnabled = bgSound,
            combatSoundEnabled = combatSound,
            hitShakeEnabled = hitShake,
            enabledSoundCount = enabledCount,
            showDevelopSettings = BuildConfig.DEBUG
        )
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
