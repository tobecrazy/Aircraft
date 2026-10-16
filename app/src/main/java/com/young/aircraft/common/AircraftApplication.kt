package com.young.aircraft.common

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.data.RoomApiHistoryStore
import com.young.developtools.data.LogSettings
import com.young.aircraft.gui.HistoryActivity
import com.young.developtools.DevTools
import com.young.developtools.utils.DevLog
import com.young.aircraft.data.GameState
import com.young.aircraft.gui.MainActivity
import com.young.aircraft.gui.MandatoryUpdateActivity
import com.young.aircraft.gui.openUpdatePage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import com.young.aircraft.gui.dialogs.showThemed
import com.young.aircraft.utils.AppLog
import com.young.aircraft.utils.GameInterruptionShield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Create by Young
 **/
class AircraftApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var foregroundActivity = WeakReference<Activity>(null)
    private var optionalPromptedVersion: String? = null
    private var optionalUpdateDialog: AlertDialog? = null
    private var welcomedConfigId: Long? = null
    private var welcomedRepoMessage: String? = null

    override fun onCreate() {
        super.onCreate()
        GameInterruptionShield.restoreAfterProcessDeath(this)
        LogSettings.defaultEnabled = BuildConfig.DEBUG
        AppLog.enabled = BuildConfig.DEBUG
        DevLog.enabled = BuildConfig.DEBUG
        wireDevTools()
        AircraftRemoteConfig.onConfigActivated = {
            mainHandler.post {
                LauncherIconManager.apply(this, RepoUpdateConfigStore.getAppIconVariant())
                enforceMinimumVersion()
            }
        }
        AircraftRemoteConfig.initialize()
        // Repo JSON is the fallback whenever Remote Config holds no remote value
        // (unreachable or key absent). A Firebase REMOTE value always wins when
        // present, so regions where Firebase works are unaffected.
        RepoUpdateConfigStore.onUpdated = {
            mainHandler.post { enforceMinimumVersion() }
        }
        RepoUpdateConfigStore.refresh(applicationScope)
        // Apply the cached/default value immediately; fetch and real-time updates re-apply later.
        LauncherIconManager.apply(this, RepoUpdateConfigStore.getAppIconVariant())
        val logSettings = LogSettings(this)
        applicationScope.launch {
            logSettings.enabledFlow
                .catch { AppLog.enabled = LogSettings.defaultEnabled }
                .collect { AppLog.enabled = it }
        }
        // Lock portrait on phone-width screens; leave free rotation on large screens
        // (tablets / unfolded foldables), where the platform ignores the lock anyway.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyOrientation(activity)
                if (!BuildConfig.DEBUG) {
                    activity.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                applyOrientation(activity)
                foregroundActivity = WeakReference(activity)
                enforceMinimumVersion(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (foregroundActivity.get() === activity) foregroundActivity.clear()
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun applyOrientation(activity: Activity) {
        if (activity.resources.configuration.smallestScreenWidthDp < 600) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            // UNSPECIFIED defers to the user's rotation lock / sensor, unlike FULL_USER.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun enforceMinimumVersion(activity: Activity? = foregroundActivity.get()) {
        if (activity == null || activity.isFinishing) return
        // Suppress all in-app dialogs (forced/optional update, welcome) during combat;
        // the next onActivityResumed after combat re-triggers them so dogfights stay uninterrupted.
        if (activity is MainActivity) return
        val updateRequired = RepoUpdateConfigStore.isCurrentVersionBelowMinimum()
        if (activity is MandatoryUpdateActivity) {
            if (!updateRequired) activity.finish()
            return
        }
        if (!updateRequired) {
            if (!showOptionalUpdatePrompt(activity)) showWelcomeDialog(activity)
            return
        }

        activity.startActivity(
            Intent(activity, MandatoryUpdateActivity::class.java)
        )
    }

    private fun showOptionalUpdatePrompt(activity: Activity): Boolean {
        if (optionalUpdateDialog?.isShowing == true) return true
        if (!RepoUpdateConfigStore.isOptionalUpdateAvailable()) return false
        val latestVersion = RepoUpdateConfigStore.getLatestVersion()
        if (optionalPromptedVersion == latestVersion) return false
        optionalPromptedVersion = latestVersion

        // Best-effort prompt: never crash the resume path when the host theme
        // cannot render Material dialogs (e.g. plain test Activities).
        try {
            optionalUpdateDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.remote_update_available_title)
            .setMessage(
                activity.getString(
                    R.string.remote_update_available_message,
                    BuildConfig.VERSION_NAME,
                    latestVersion
                )
            )
            .setPositiveButton(R.string.remote_update_available_action) { _, _ ->
                openUpdatePage(activity)
            }
            .setNegativeButton(R.string.remote_update_later_action, null)
            .setCancelable(true)
            .showThemed {
                optionalUpdateDialog = null
                if (foregroundActivity.get() === activity && !activity.isFinishing) {
                    showWelcomeDialog(activity)
                }
            }
            return true
            AppLog.w("AircraftApplication", "Host theme cannot show update prompt; skipping")
            return false
        }
    }

    private fun showWelcomeDialog(activity: Activity) {
        if (activity is MandatoryUpdateActivity || activity is MainActivity || activity.isFinishing) return
        if (foregroundActivity.get() !== activity) return
        val tokenConfig = AircraftRemoteConfig.getTokenConfig()
        if (tokenConfig != null && tokenConfig.showWelcome && tokenConfig.welcomeMessage.isNotBlank()) {
            if (welcomedConfigId == tokenConfig.id) return
            welcomedConfigId = tokenConfig.id
            showWelcomeMessage(activity, tokenConfig.welcomeMessage)
            return
        }
        // Firebase token absent or welcome disabled: repo JSON fallback with
        // the same gating (showWelcome && non-blank message), shown once per
        // message per process.
        val repo = RepoUpdateConfigStore.cached
        if (repo == null || !repo.showWelcome || repo.welcomeMessage.isBlank()) return
        if (welcomedRepoMessage == repo.welcomeMessage) return
        welcomedRepoMessage = repo.welcomeMessage
        showWelcomeMessage(activity, repo.welcomeMessage)
    }

    private fun showWelcomeMessage(activity: Activity, message: String) {
        // Best-effort message: skip instead of crashing when the host theme
        // cannot render Material dialogs (e.g. plain test Activities).
        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.remote_welcome_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(true)
                .showThemed()
        } catch (_: IllegalArgumentException) {
            AppLog.w("AircraftApplication", "Host theme cannot show welcome message; skipping")
        }
    }

    private fun wireDevTools() {
        DevTools.openActivityMonitor = { context ->
            context.startActivity(
                Intent(context, HistoryActivity::class.java)
            )
        }
        DevTools.onInvincibleChanged = { enabled ->
            GameStateManager.isInvincible = enabled
        }
        DevTools.historyStoreProvider = { context ->
            RoomApiHistoryStore(context)
        }
        DevTools.onLogEnabledChanged = { enabled ->
            AppLog.enabled = enabled
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        GameStateManager.emit(GameState.LOW_MEMORY)
    }


}
