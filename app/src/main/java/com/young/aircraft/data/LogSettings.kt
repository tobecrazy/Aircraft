package com.young.aircraft.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.young.aircraft.utils.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.logDataStore by preferencesDataStore(name = "log_settings")

class LogSettings(context: Context) {
    private val appContext = context.applicationContext
    private val enabledKey = booleanPreferencesKey("log_enabled")

    val enabledFlow: Flow<Boolean> = appContext.logDataStore.data
        .map { preferences -> preferences[enabledKey] ?: defaultEnabled }

    suspend fun setEnabled(value: Boolean) {
        appContext.logDataStore.edit { preferences -> preferences[enabledKey] = value }
        AppLog.enabled = value
    }

    companion object {
        @Volatile
        var defaultEnabled: Boolean = false
    }
}
