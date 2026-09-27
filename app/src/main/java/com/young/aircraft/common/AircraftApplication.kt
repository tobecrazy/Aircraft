package com.young.aircraft.common

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.tencent.bugly.beta.Beta
import com.tencent.bugly.crashreport.CrashReport
import com.young.aircraft.BuildConfig
import com.young.aircraft.data.GameState
import com.young.aircraft.data.SettingsRepository

/**
 * Create by Young
 **/
class AircraftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initBuglyIfConsented()
        // Lock portrait on phone-width screens; leave free rotation on large screens
        // (tablets / unfolded foldables), where the platform ignores the lock anyway.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyOrientation(activity)
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
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

    /**
     * Bugly is used for app upgrade only (crash reporting stays with Firebase
     * Crashlytics), and must only start after privacy-policy consent (个人信息保护法).
     * App ID / channel are configured via meta-data in the manifest
     * (placeholders in app/build.gradle.kts).
     * Also invoked from [com.young.aircraft.gui.PrivacyPolicyAcceptActivity]
     * right after the policy is accepted, so the first-run session is covered.
     */
    fun initBuglyIfConsented() {
        if (!SettingsRepository(this).isPrivacyPolicyAccepted()) return
        try {
            Beta.init(this, BuildConfig.DEBUG)
            // The upgrade SDK bundles the crashreport module; disable its crash
            // upload (must be called after init — see closeCrashReport's guard).
            CrashReport.closeNativeReport()
            CrashReport.closeCrashReport()
        } catch (e: Throwable) {
            // Third-party SDK init must never take the app down (also covers Robolectric).
            Log.w(TAG, "Bugly upgrade init skipped", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        GameStateManager.emit(GameState.LOW_MEMORY)
    }

    private companion object {
        const val TAG = "AircraftApplication"
    }
}