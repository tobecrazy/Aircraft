package com.young.aircraft.utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Combat Do-Not-Disturb shield: suppresses system notification heads-up popups while a
 * dogfight is in progress.
 *
 * Platform limitation: [NotificationManager.setInterruptionFilter] only takes effect after
 * the user grants Do-Not-Disturb access in system settings; returns null when unauthorized,
 * and callers fall back to auto-pause (see MainActivity.onPause/onWindowFocusChanged).
 */
object GameInterruptionShield {
    private const val PREFERENCES_NAME = "game_interruption_shield"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun isGranted(context: Context): Boolean {
        if (!isSupported()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return try {
            manager.isNotificationPolicyAccessGranted
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Call on combat entry: when granted, switches to full suppression and returns the
     * previous filter (for restore on exit); returns null when unauthorized/unsupported/failed.
     */
    fun enter(context: Context): Int? {
        if (!isSupported()) return null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return null
        return try {
            if (!manager.isNotificationPolicyAccessGranted) return null
            val previous = manager.currentInterruptionFilter
            if (previous == NotificationManager.INTERRUPTION_FILTER_NONE) return null
            val preferences = preferences(context)
            if (!preferences.edit().putInt(KEY_PREVIOUS_FILTER, previous).commit()) return null
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            previous
        } catch (_: SecurityException) {
            AppLog.w("GameInterruptionShield", "DND access revoked, skip shielding")
            null
        } catch (_: Exception) {
            AppLog.w("GameInterruptionShield", "Failed to enable DND shielding")
            null
        }
    }

    /** Call on combat exit: restores the previously saved filter. */
    fun exit(context: Context, previousFilter: Int?) {
        val target = previousFilter ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            if (!manager.isNotificationPolicyAccessGranted) return
            val current = manager.currentInterruptionFilter
            if (shouldRestorePreviousFilter(current, target)) {
                manager.setInterruptionFilter(target)
            }
            clearSavedFilter(context)
        } catch (_: SecurityException) {
            AppLog.w("GameInterruptionShield", "DND access revoked, skip restoring")
        } catch (_: Exception) {
            AppLog.w("GameInterruptionShield", "Failed to restore interruption filter")
        }
    }

    /** Recover a filter left behind if Android killed the process before onPause ran. */
    fun restoreAfterProcessDeath(context: Context) {
        val preferences = preferences(context)
        if (!preferences.contains(KEY_PREVIOUS_FILTER)) return
        val previous = preferences.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            if (!manager.isNotificationPolicyAccessGranted) return
            val current = manager.currentInterruptionFilter
            if (shouldRestorePreviousFilter(current, previous)) {
                manager.setInterruptionFilter(previous)
            }
            clearSavedFilter(context)
        } catch (_: SecurityException) {
            AppLog.w("GameInterruptionShield", "DND access revoked, skip recovery")
        } catch (_: Exception) {
            AppLog.w("GameInterruptionShield", "Failed to recover interruption filter")
        }
    }

    fun dndSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun clearSavedFilter(context: Context) {
        preferences(context).edit().remove(KEY_PREVIOUS_FILTER).commit()
    }
}

/** Only restores when the current state is still the full suppression set by this feature, preserving any filter the user changed afterwards. */
internal fun shouldRestorePreviousFilter(currentFilter: Int, previousFilter: Int): Boolean =
    currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE &&
        previousFilter != NotificationManager.INTERRUPTION_FILTER_NONE
