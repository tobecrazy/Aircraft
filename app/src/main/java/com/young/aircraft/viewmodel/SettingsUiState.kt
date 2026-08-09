package com.young.aircraft.viewmodel

import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository

data class SettingsUiState(
    val difficulty: GameDifficulty = GameDifficulty.NORMAL,
    val bgSoundEnabled: Boolean = true,
    val combatSoundEnabled: Boolean = true,
    val hitShakeEnabled: Boolean = true,
    val enabledSoundCount: Int = 3,
    val showDevelopSettings: Boolean = false,
    val bgmFormat: String = SettingsRepository.BGM_FORMAT_MP3
)
