package com.young.developtools

import android.content.Context
import com.young.developtools.repository.ApiHistoryStore
import com.young.developtools.repository.InMemoryApiHistoryStore

/**
 * Host bridge: the module never references :app classes directly.
 * [AircraftApplication][com.young.aircraft.common.AircraftApplication] wires the four hooks
 * in `onCreate`; every hook has a safe fallback so the module also runs standalone.
 */
object DevTools {
    /**
     * Opens the game-side activity monitor (HistoryActivity lives in :app and cannot be
     * referenced from here). Null when unwired: the caller must toast "unavailable".
     */
    var openActivityMonitor: ((Context) -> Unit)? = null

    /**
     * Mirrors the persisted invincible flag into the game engine
     * ([GameStateManager][com.young.aircraft.common.GameStateManager] lives in :app).
     */
    var onInvincibleChanged: ((Boolean) -> Unit)? = null

    /** Room-backed history store provided by :app; null falls back to [InMemoryApiHistoryStore]. */
    var historyStoreProvider: ((Context) -> ApiHistoryStore)? = null

    /** Forwards the log kill-switch to the app-wide AppLog; see [DevLog]. */
    var onLogEnabledChanged: ((Boolean) -> Unit)? = null

    fun resolveHistoryStore(context: Context): ApiHistoryStore =
        runCatching { historyStoreProvider?.invoke(context) }.getOrNull()
            ?: InMemoryApiHistoryStore()

    /** Reads the host app version without touching :app's BuildConfig. */
    fun hostVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)?.versionName
    }.getOrNull() ?: "?"
}
