package com.young.developtools.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.developtools.utils.DevPrefs

class DevelopSettingsViewModel(
    private val prefs: DevPrefs
) : ViewModel() {

    fun isInvincibleModeEnabled(): Boolean = prefs.isInvincibleModeEnabled()

    fun setInvincibleModeEnabled(enabled: Boolean) {
        prefs.setInvincibleModeEnabled(enabled)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val prefs = DevPrefs(context.applicationContext)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DevelopSettingsViewModel(prefs) as T
        }
    }
}
