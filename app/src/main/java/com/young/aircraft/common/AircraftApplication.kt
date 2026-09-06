package com.young.aircraft.common

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import com.young.aircraft.data.GameState

/**
 * Create by Young
 **/
class AircraftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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