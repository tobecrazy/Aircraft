package com.young.aircraft.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.young.aircraft.data.GameDifficulty
import java.util.UUID

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)

    init {
        migrateLegacySettingsIfNeeded()
    }

    fun getDifficulty(): GameDifficulty {
        return GameDifficulty.fromPersistedValue(prefs.getString(KEY_DIFFICULTY, GameDifficulty.NORMAL.persistedValue))
    }

    fun setDifficulty(difficulty: GameDifficulty) {
        prefs.edit { putString(KEY_DIFFICULTY, difficulty.persistedValue) }
    }

    fun isBackgroundSoundEnabled(): Boolean = prefs.getBoolean(KEY_BACKGROUND_SOUND, true)

    fun setBackgroundSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BACKGROUND_SOUND, enabled) }
    }

    fun isCombatSoundEnabled(): Boolean = prefs.getBoolean(KEY_COMBAT_SOUND, true)

    fun setCombatSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_COMBAT_SOUND, enabled) }
    }

    fun isHitShakeEffectEnabled(): Boolean = prefs.getBoolean(KEY_HIT_SHAKE_EFFECT, true)

    fun setHitShakeEffectEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HIT_SHAKE_EFFECT, enabled) }
    }

    fun isInvincibleModeEnabled(): Boolean = prefs.getBoolean(KEY_INVINCIBLE_MODE, false)

    fun setInvincibleModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INVINCIBLE_MODE, enabled) }
    }

    fun isPrivacyPolicyAccepted(): Boolean = prefs.getBoolean(KEY_PRIVACY_POLICY_ACCEPTED, false)

    fun setPrivacyPolicyAccepted(accepted: Boolean) {
        prefs.edit { putBoolean(KEY_PRIVACY_POLICY_ACCEPTED, accepted) }
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
    }

    fun getOrCreateInstallId(): String {
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val generatedId = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_INSTALL_ID, generatedId) }
        return generatedId
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun migrateLegacySettingsIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_SETTINGS_MIGRATED, false)) {
            return
        }

        prefs.edit {
            if (!prefs.contains(KEY_DIFFICULTY) && legacyPrefs.contains(KEY_DIFFICULTY)) {
                putString(
                    KEY_DIFFICULTY,
                    legacyPrefs.getString(KEY_DIFFICULTY, GameDifficulty.NORMAL.persistedValue)
                )
            }
            if (!prefs.contains(KEY_BACKGROUND_SOUND) && legacyPrefs.contains(KEY_BACKGROUND_SOUND)) {
                putBoolean(KEY_BACKGROUND_SOUND, legacyPrefs.getBoolean(KEY_BACKGROUND_SOUND, true))
            }
            if (!prefs.contains(KEY_COMBAT_SOUND) && legacyPrefs.contains(KEY_COMBAT_SOUND)) {
                putBoolean(KEY_COMBAT_SOUND, legacyPrefs.getBoolean(KEY_COMBAT_SOUND, true))
            }
            if (!prefs.contains(KEY_HIT_SHAKE_EFFECT) && legacyPrefs.contains(KEY_HIT_SHAKE_EFFECT)) {
                putBoolean(KEY_HIT_SHAKE_EFFECT, legacyPrefs.getBoolean(KEY_HIT_SHAKE_EFFECT, true))
            }
            if (!prefs.contains(KEY_INVINCIBLE_MODE) && legacyPrefs.contains(KEY_INVINCIBLE_MODE)) {
                putBoolean(KEY_INVINCIBLE_MODE, legacyPrefs.getBoolean(KEY_INVINCIBLE_MODE, false))
            }
            putBoolean(KEY_LEGACY_SETTINGS_MIGRATED, true)
        }
    }

    companion object {
        const val PREFS_NAME = "aircraft_prefs"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_BACKGROUND_SOUND = "background_sound"
        const val KEY_COMBAT_SOUND = "combat_sound"
        const val KEY_HIT_SHAKE_EFFECT = "hit_shake_effect"
        const val KEY_INVINCIBLE_MODE = "invincible_mode"
        const val KEY_PRIVACY_POLICY_ACCEPTED = "privacy_policy_accepted"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_INSTALL_ID = "install_id"
        private const val KEY_LEGACY_SETTINGS_MIGRATED = "legacy_settings_migrated"
    }
}
