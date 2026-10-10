package com.young.developtools.utils

import android.content.Context

/**
 * Release-safe gate for developer screens. Debug builds are always enabled via
 * [DebugTools.isEnabled]; release builds additionally honor a persistent unlock
 * flag earned by tapping the About Aircraft version badge [TAPS_TO_UNLOCK] times.
 *
 * State lives in the shared prefs file also used by the app's SettingsRepository
 * (see DevPrefs); both sides read the same bytes, so the unlock survives this move.
 */
object DeveloperMode {
    const val TAPS_TO_UNLOCK = 8

    fun isEnabled(context: Context): Boolean =
        DebugTools.isEnabled || DevPrefs(context).isDeveloperOptionsUnlocked()

    /**
     * Persists the unlock flag. Returns true only when this call newly unlocked;
     * callers use that to decide whether to show the "enabled" confirmation.
     */
    fun unlock(context: Context): Boolean {
        val prefs = DevPrefs(context)
        if (prefs.isDeveloperOptionsUnlocked()) return false
        prefs.setDeveloperOptionsUnlocked(true)
        return true
    }

    /** True on every [TAPS_TO_UNLOCK]-th tap (8th, 16th, ...). Public: the host app's unlock entry calls it. */
    fun isUnlockTap(tapCount: Int): Boolean =
        tapCount > 0 && tapCount % TAPS_TO_UNLOCK == 0

    /** Remaining taps before the next unlock boundary; only meaningful off-boundary. */
    fun remainingTaps(tapCount: Int): Int =
        TAPS_TO_UNLOCK - (tapCount % TAPS_TO_UNLOCK)
}
