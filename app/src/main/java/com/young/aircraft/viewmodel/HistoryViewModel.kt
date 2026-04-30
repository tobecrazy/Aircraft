package com.young.aircraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.utils.HallOfHeroesNameUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val dao: PlayerGameDataDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val records = dao.getAllByScoreDesc()
            _uiState.value = computeState(records)
        }
    }

    fun deleteRecord(item: PlayerGameData) {
        viewModelScope.launch {
            dao.delete(item)
            loadHistory()
        }
    }

    private fun computeState(records: List<PlayerGameData>): HistoryUiState {
        val topRecord = records.firstOrNull()
        return HistoryUiState(
            records = records,
            isLoading = false,
            bestScore = topRecord?.score,
            topPilotName = topRecord?.let { HallOfHeroesNameUtils.getDisplayName(it) },
            topPilotLevel = topRecord?.level,
            recordCount = records.size
        )
    }

    class Factory(private val dao: PlayerGameDataDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(dao) as T
        }
    }
}
