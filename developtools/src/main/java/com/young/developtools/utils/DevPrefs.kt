package com.young.developtools.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Prefs accessor for the developer-tool state. Reads/writes the SAME SharedPreferences
 * file (`aircraft_prefs`) and keys as the app's SettingsRepository, so the extraction
 * migrates zero bytes: the invincible flag and the 8-tap unlock survive the move.
 *
 * Source of truth for key names stays in SettingsRepository; the constants below are
 * intentionally duplicated (with a comment each) because the module cannot depend on :app.
 */
class DevPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(): String = prefs.getString(KEY_THEME, THEME_GREEN)
        ?.takeIf { it in THEME_IDS }
        ?: THEME_GREEN

    fun setTheme(theme: String) {
        require(theme in THEME_IDS) { "unknown theme: $theme" }
        prefs.edit { putString(KEY_THEME, theme) }
    }

    fun isInvincibleModeEnabled(): Boolean = prefs.getBoolean(KEY_INVINCIBLE_MODE, false)

    fun setInvincibleModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INVINCIBLE_MODE, enabled) }
    }

    fun isDeveloperOptionsUnlocked(): Boolean =
        prefs.getBoolean(KEY_DEVELOPER_OPTIONS_UNLOCKED, false)

    fun setDeveloperOptionsUnlocked(unlocked: Boolean) {
        prefs.edit { putBoolean(KEY_DEVELOPER_OPTIONS_UNLOCKED, unlocked) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        // Mirrors SettingsRepository.PREFS_NAME / KEY_THEME / THEME_*.
        const val PREFS_NAME = "aircraft_prefs"
        const val KEY_THEME = "theme"
        const val THEME_GREEN = "green"
        const val THEME_BLUE = "blue"
        const val THEME_PURPLE = "purple"
        const val THEME_YELLOW = "yellow"
        const val THEME_RED = "red"
        const val THEME_DYNAMIC = "dynamic"

        val THEME_IDS = setOf(THEME_GREEN, THEME_BLUE, THEME_PURPLE, THEME_YELLOW, THEME_RED, THEME_DYNAMIC)

        // Mirrors SettingsRepository.KEY_INVINCIBLE_MODE / KEY_DEVELOPER_OPTIONS_UNLOCKED.
        const val KEY_INVINCIBLE_MODE = "invincible_mode"
        const val KEY_DEVELOPER_OPTIONS_UNLOCKED = "developer_options_unlocked"
    }
}
