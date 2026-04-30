package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.providers.SettingsRepository

class DevelopSettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    fun isInvincibleModeEnabled(): Boolean = repository.isInvincibleModeEnabled()

    fun setInvincibleModeEnabled(enabled: Boolean) {
        repository.setInvincibleModeEnabled(enabled)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val repository = SettingsRepository(context)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DevelopSettingsViewModel(repository) as T
        }
    }
}
