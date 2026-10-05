package com.young.aircraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.SettingsRepository

class PrivacyPolicyViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    fun isAlreadyAccepted(): Boolean = repository.isPrivacyPolicyAccepted()

    /** True when a previous acceptance exists but predates the current policy text. */
    fun hasAcceptedEarlierVersion(): Boolean =
        repository.acceptedPolicyVersion() in 1 until AircraftConstants.PrivacyPolicy.POLICY_VERSION

    fun acceptedPolicyVersion(): Int = repository.acceptedPolicyVersion()

    fun acceptPolicy() {
        repository.setPrivacyPolicyAccepted(true)
    }

    fun withdrawPolicy() {
        repository.setPrivacyPolicyAccepted(false)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val repository = SettingsRepository(context)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PrivacyPolicyViewModel(repository) as T
        }
    }
}
