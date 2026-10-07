package com.young.aircraft.utils

import android.content.Context
import com.young.aircraft.data.SettingsRepository

/**
 * Release-safe gate for developer screens. Debug builds are always enabled via
 * [DebugTools.isEnabled]; release builds additionally honor a persistent unlock
 * flag earned by tapping the About Aircraft version badge [TAPS_TO_UNLOCK] times.
 */
object DeveloperMode {
    const val TAPS_TO_UNLOCK = 8

    fun isEnabled(context: Context): Boolean =
        DebugTools.isEnabled || SettingsRepository(context).isDeveloperOptionsUnlocked()

    /**
     * Persists the unlock flag. Returns true only when this call newly unlocked;
     * callers use that to decide whether to show the "enabled" confirmation.
     */
    fun unlock(context: Context): Boolean {
        val repository = SettingsRepository(context)
        if (repository.isDeveloperOptionsUnlocked()) return false
        repository.setDeveloperOptionsUnlocked(true)
        return true
    }

    /** True on every [TAPS_TO_UNLOCK]-th tap (8th, 16th, ...). */
    internal fun isUnlockTap(tapCount: Int): Boolean =
        tapCount > 0 && tapCount % TAPS_TO_UNLOCK == 0

    /** Remaining taps before the next unlock boundary; only meaningful off-boundary. */
    internal fun remainingTaps(tapCount: Int): Int =
        TAPS_TO_UNLOCK - (tapCount % TAPS_TO_UNLOCK)
}
