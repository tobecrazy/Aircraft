package com.young.aircraft.gui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val dao: PlayerGameDataDao) : ViewModel() {

    private val _records = MutableStateFlow<List<PlayerGameData>>(emptyList())
    val records: StateFlow<List<PlayerGameData>> = _records.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _records.value = dao.getAllByScoreDesc()
        }
    }

    fun deleteRecord(record: PlayerGameData) {
        viewModelScope.launch {
            dao.delete(record)
            loadHistory()
        }
    }
}
