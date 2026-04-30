package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.providers.SettingsRepository

class OnboardingViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    fun isAlreadyCompleted(): Boolean = repository.isOnboardingCompleted()

    fun completeOnboarding() {
        repository.setOnboardingCompleted(true)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val repository = SettingsRepository(context)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(repository) as T
        }
    }
}
