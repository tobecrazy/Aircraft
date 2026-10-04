package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.LogSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogSettingsViewModel(private val settings: LogSettings) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.young.aircraft.utils.AppLog.enabled
    )

    fun onToggle(value: Boolean) {
        viewModelScope.launch { settings.setEnabled(value) }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val settings = LogSettings(context.applicationContext)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LogSettingsViewModel(settings) as T
    }
}
