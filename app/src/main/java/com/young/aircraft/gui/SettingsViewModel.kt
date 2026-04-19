package com.young.aircraft.gui

import androidx.lifecycle.ViewModel
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.providers.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _difficulty = MutableStateFlow(repository.getDifficulty())
    val difficulty: StateFlow<GameDifficulty> = _difficulty.asStateFlow()

    private val _backgroundSoundEnabled = MutableStateFlow(repository.isBackgroundSoundEnabled())
    val backgroundSoundEnabled: StateFlow<Boolean> = _backgroundSoundEnabled.asStateFlow()

    private val _combatSoundEnabled = MutableStateFlow(repository.isCombatSoundEnabled())
    val combatSoundEnabled: StateFlow<Boolean> = _combatSoundEnabled.asStateFlow()

    private val _hitShakeEffectEnabled = MutableStateFlow(repository.isHitShakeEffectEnabled())
    val hitShakeEffectEnabled: StateFlow<Boolean> = _hitShakeEffectEnabled.asStateFlow()

    fun setDifficulty(difficulty: GameDifficulty) {
        repository.setDifficulty(difficulty)
        _difficulty.value = difficulty
    }

    fun setBackgroundSoundEnabled(enabled: Boolean) {
        repository.setBackgroundSoundEnabled(enabled)
        _backgroundSoundEnabled.value = enabled
    }

    fun setCombatSoundEnabled(enabled: Boolean) {
        repository.setCombatSoundEnabled(enabled)
        _combatSoundEnabled.value = enabled
    }

    fun setHitShakeEffectEnabled(enabled: Boolean) {
        repository.setHitShakeEffectEnabled(enabled)
        _hitShakeEffectEnabled.value = enabled
    }
}
