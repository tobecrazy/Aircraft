package com.young.aircraft.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Fail-safe Crashlytics facade for error-handling paths.
 *
 * Crashlytics is optional telemetry: when Firebase is not initialized (plain
 * JVM/Robolectric tests) or the SDK rejects a call, reporting must never break
 * the path that is already handling a failure, so every entry point swallows
 * its own errors.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    /** Records a breadcrumb that ships with the next crash report. */
    fun log(message: String) {
        val crashlytics = instance() ?: return
        try {
            crashlytics.log(message)
        } catch (error: Exception) {
            AppLog.w(TAG, "Crashlytics log failed", error)
        }
    }

    /**
     * Reports [error] as a non-fatal crash and attaches [keys] as custom keys,
     * so the report carries the state that produced the failure.
     */
    fun record(error: Throwable, vararg keys: Pair<String, Any>) {
        val crashlytics = instance() ?: return
        try {
            keys.forEach { (key, value) -> applyCustomKey(crashlytics, key, value) }
            crashlytics.recordException(error)
        } catch (failure: Exception) {
            AppLog.w(TAG, "Crashlytics record failed", failure)
        }
    }

    private fun applyCustomKey(crashlytics: FirebaseCrashlytics, key: String, value: Any) {
        when (value) {
            is String -> crashlytics.setCustomKey(key, value)
            is Boolean -> crashlytics.setCustomKey(key, value)
            is Int -> crashlytics.setCustomKey(key, value)
            is Long -> crashlytics.setCustomKey(key, value)
            is Float -> crashlytics.setCustomKey(key, value)
            is Double -> crashlytics.setCustomKey(key, value)
            else -> crashlytics.setCustomKey(key, value.toString())
        }
    }

    /** FirebaseApp is absent in plain JVM tests; telemetry simply stays off. */
    private fun instance(): FirebaseCrashlytics? = try {
        FirebaseCrashlytics.getInstance()
    } catch (_: Throwable) {
        null
    }
}
