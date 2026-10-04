package com.young.aircraft.common

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import com.young.aircraft.BuildConfig
import com.young.aircraft.data.LogSettings
import com.young.aircraft.data.GameState
import com.young.aircraft.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Create by Young
 **/
class AircraftApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LogSettings.defaultEnabled = BuildConfig.DEBUG
        AppLog.enabled = BuildConfig.DEBUG
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
            }
            override fun onActivityPaused(activity: Activity) {}
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

    override fun onLowMemory() {
        super.onLowMemory()
        GameStateManager.emit(GameState.LOW_MEMORY)
    }


}
