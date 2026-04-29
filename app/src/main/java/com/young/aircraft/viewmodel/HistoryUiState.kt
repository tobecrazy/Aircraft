package com.young.aircraft.viewmodel

import com.young.aircraft.data.PlayerGameData

data class HistoryUiState(
    val records: List<PlayerGameData> = emptyList(),
    val isLoading: Boolean = true,
    val bestScore: Long? = null,
    val topPilotName: String? = null,
    val topPilotLevel: Int? = null,
    val recordCount: Int = 0
)
