package com.young.aircraft.common

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.young.aircraft.BuildConfig
import com.young.aircraft.utils.AppLog
import org.json.JSONObject
import java.math.BigInteger
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class RemoteTokenConfig(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val expiringAt: Instant,
    val enable: Boolean,
    val welcomeMessage: String = "",
    val showWelcome: Boolean = false
)

private const val DEFAULT_APP_ICON_VARIANT = 1

/** Application-wide Remote Config setup and typed parameter access. */
object AircraftRemoteConfig {
    private const val TAG = "AircraftRemoteConfig"
    private const val RELEASE_FETCH_INTERVAL_SECONDS = 60 * 60L
    const val TOKEN_CONFIG_PARAMETER = "Aircraft2026"
    const val MINIMUM_VERSION_PARAMETER = "minimum_version"
    const val LATEST_VERSION_PARAMETER = "latest_version"
    const val UPDATE_URL_PARAMETER = "update_url"
    const val APP_ICON_PARAMETER = "AppIcon"
    private const val DEFAULT_MINIMUM_VERSION = "1.4.1"

    private val defaults: Map<String, Any> = mapOf(
        TOKEN_CONFIG_PARAMETER to """{"id":0,"name":"","createdAt":"1970-01-01T00:00:00Z","expiringAt":"1970-01-01T00:00:00Z","enable":false}""",
        MINIMUM_VERSION_PARAMETER to DEFAULT_MINIMUM_VERSION,
        LATEST_VERSION_PARAMETER to "",
        UPDATE_URL_PARAMETER to "",
        APP_ICON_PARAMETER to DEFAULT_APP_ICON_VARIANT.toString()
    )
    private var updateRegistration: ConfigUpdateListenerRegistration? = null
    private var initializationStarted = false
    @Volatile
    var onConfigActivated: (() -> Unit)? = null

    val instance: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()

    @Synchronized
    fun initialize() {
        if (initializationStarted) return
        initializationStarted = true

        val remoteConfig = try {
            FirebaseRemoteConfig.getInstance()
        } catch (error: IllegalStateException) {
            AppLog.w(TAG, "Firebase is not initialized; skipping Remote Config setup", error)
            return
        }
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        AppLog.d(TAG) { "Crashlytics collection disabled until token config is loaded" }

        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                // This limits ordinary fetches; real-time updates bypass this interval.
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                    0
                } else {
                    RELEASE_FETCH_INTERVAL_SECONDS
                }
            }
        ).addOnCompleteListener { settingsTask ->
            if (!settingsTask.isSuccessful) {
                AppLog.w(TAG, "Failed to apply Remote Config settings", settingsTask.exception)
            }
            remoteConfig.setDefaultsAsync(defaults).addOnCompleteListener { defaultsTask ->
                if (!defaultsTask.isSuccessful) {
                    AppLog.w(TAG, "Failed to apply Remote Config defaults", defaultsTask.exception)
                }
                updateCrashlyticsCollection()
                listenForUpdates(remoteConfig)
                fetchAndActivate(remoteConfig)
            }
        }
    }

    private fun listenForUpdates(remoteConfig: FirebaseRemoteConfig) {
        updateRegistration = remoteConfig.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    remoteConfig.activate()
                        .addOnSuccessListener {
                            updateCrashlyticsCollection()
                            onConfigActivated?.invoke()
                            AppLog.d(TAG) {
                                "Activated real-time config keys: ${configUpdate.updatedKeys}"
                            }
                        }
                        .addOnFailureListener { error ->
                            AppLog.w(TAG, "Failed to activate real-time config", error)
                        }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    AppLog.w(TAG, "Real-time config update failed (code=${error.code})", error)
                }
            }
        )
        AppLog.d(TAG) { "Real-time Remote Config listener registered" }
    }

    private fun fetchAndActivate(remoteConfig: FirebaseRemoteConfig) {
        // Defaults remain available if the network is unavailable or fetching fails.
        AppLog.d(TAG) { "Starting initial Remote Config fetch" }
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { updated ->
                updateCrashlyticsCollection()
                onConfigActivated?.invoke()
                AppLog.d(TAG) {
                    "Initial Remote Config fetch completed (updated=$updated, " +
                        "tokenConfigSource=${valueSource(remoteConfig.getValue(TOKEN_CONFIG_PARAMETER).source)})"
                }
            }
            .addOnFailureListener { error ->
                AppLog.w(TAG, "Remote config fetch failed; using cached/default values", error)
            }
    }

    fun getBoolean(key: String): Boolean = try {
        instance.getBoolean(key)
    } catch (_: IllegalStateException) {
        defaults[key]?.toString()?.toBooleanStrictOrNull() ?: false
    }

    fun getDouble(key: String): Double = try {
        instance.getDouble(key)
    } catch (_: IllegalStateException) {
        defaults[key]?.toString()?.toDoubleOrNull() ?: 0.0
    }

    fun getLong(key: String): Long = try {
        instance.getLong(key)
    } catch (_: IllegalStateException) {
        defaults[key]?.toString()?.toLongOrNull() ?: 0L
    }

    fun getString(key: String): String = try {
        instance.getString(key)
    } catch (_: IllegalStateException) {
        defaults[key]?.toString().orEmpty()
    }

    fun getTokenConfig(): RemoteTokenConfig? = getTokenConfig(TOKEN_CONFIG_PARAMETER)

    fun getMinimumVersion(): String = getString(MINIMUM_VERSION_PARAMETER)

    fun getLatestVersion(): String = getString(LATEST_VERSION_PARAMETER)

    fun getUpdateUrl(): String = getString(UPDATE_URL_PARAMETER)

    /** Launcher icon variant from Remote Config; invalid values use the bundled default icon. */
    fun getAppIconVariant(): Int = parseAppIconVariant(getString(APP_ICON_PARAMETER))

    fun isCurrentVersionBelowMinimum(): Boolean {
        val minimumVersion = getMinimumVersion()
        val comparison = compareAppVersions(BuildConfig.VERSION_NAME, minimumVersion)
        if (comparison == null) {
            AppLog.w(TAG, "Invalid minimum app version '$minimumVersion'; using bundled version")
            return false
        }
        return comparison < 0
    }

    fun isOptionalUpdateAvailable(): Boolean {
        val currentVersion = BuildConfig.VERSION_NAME
        val minimumVersion = getMinimumVersion()
        val latestVersion = getLatestVersion()
        if (latestVersion.isBlank()) return false
        if (compareAppVersions(currentVersion, minimumVersion) == null) {
            AppLog.w(TAG, "Invalid minimum app version '$minimumVersion'; skipping optional update prompt")
            return false
        }
        if (compareAppVersions(currentVersion, latestVersion) == null) {
            AppLog.w(TAG, "Invalid latest app version '$latestVersion'; skipping optional update prompt")
            return false
        }
        return hasOptionalAppUpdate(currentVersion, minimumVersion, latestVersion)
    }

    /** Reads a JSON parameter matching the token configuration schema. */
    fun getTokenConfig(key: String): RemoteTokenConfig? {
        val json = getString(key)
        if (json.isBlank()) return null
        return parseRemoteTokenConfig(json) ?: run {
            AppLog.w(TAG, "Invalid token config in Remote Config parameter '$key'")
            null
        }
    }

    private fun updateCrashlyticsCollection() {
        val source = valueSource(instance.getValue(TOKEN_CONFIG_PARAMETER).source)
        val tokenConfig = getTokenConfig()
        val enabled = tokenConfig?.enable == true
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
        val reason = when {
            tokenConfig == null -> "'$TOKEN_CONFIG_PARAMETER' is missing or invalid"
            else -> "'$TOKEN_CONFIG_PARAMETER'.enable=${tokenConfig.enable}"
        }
        AppLog.d(TAG) { "Crashlytics collection enabled=$enabled ($reason, source=$source)" }
    }

    private fun valueSource(source: Int): String = when (source) {
        FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "REMOTE"
        FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "DEFAULT"
        else -> "STATIC"
    }

}

internal fun parseAppIconVariant(value: String): Int = value.trim().toIntOrNull()
    ?.takeIf { it in 1..5 } ?: DEFAULT_APP_ICON_VARIANT

internal fun parseRemoteTokenConfig(json: String): RemoteTokenConfig? = try {
    val value = JSONObject(json)
    RemoteTokenConfig(
        id = value.getLong("id"),
        name = value.getString("name"),
        createdAt = value.getString("createdAt").toInstant(),
        expiringAt = value.getString("expiringAt").toInstant(),
        enable = value.getBoolean("enable"),
        welcomeMessage = value.optString("welcomeMessage", ""),
        showWelcome = value.optBoolean("showWelcome", false)
    )
} catch (_: Exception) {
    null
}

internal fun compareAppVersions(current: String, required: String): Int? {
    fun components(version: String): List<BigInteger>? {
        val parts = version.split('.')
        if (parts.isEmpty() || parts.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) {
            return null
        }
        return parts.map { it.toBigIntegerOrNull() ?: return null }
    }

    val currentParts = components(current) ?: return null
    val requiredParts = components(required) ?: return null
    for (index in 0 until maxOf(currentParts.size, requiredParts.size)) {
        val currentPart = currentParts.getOrElse(index) { BigInteger.ZERO }
        val requiredPart = requiredParts.getOrElse(index) { BigInteger.ZERO }
        val result = currentPart.compareTo(requiredPart)
        if (result != 0) return result
    }
    return 0
}

internal fun hasOptionalAppUpdate(
    currentVersion: String,
    minimumVersion: String,
    latestVersion: String
): Boolean {
    if (latestVersion.isBlank()) return false
    val minimumComparison = compareAppVersions(currentVersion, minimumVersion) ?: return false
    if (minimumComparison < 0) return false
    val latestComparison = compareAppVersions(currentVersion, latestVersion) ?: return false
    return latestComparison < 0
}

private fun String.toInstant(): Instant = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    OffsetDateTime.parse(this).toInstant()
}
