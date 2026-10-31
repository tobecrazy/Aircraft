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
import com.young.aircraft.utils.CrashReporter
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
    const val WELCOME_MESSAGE_PARAMETER = "welcome_message"
    private const val DEFAULT_MINIMUM_VERSION = "1.5.6"

    /**
     * Placeholder accepted in Firebase Remote Config and app-update.json.
     * It means "use the bundled, localized [com.young.aircraft.R.string.welcome_message]",
     * so a single remote value renders correctly in every language.
     */
    const val WELCOME_MESSAGE_PLACEHOLDER = "{{welcome_message}}"

    private val defaults: Map<String, Any> = mapOf(
        TOKEN_CONFIG_PARAMETER to """{"id":0,"name":"","createdAt":"1970-01-01T00:00:00Z","expiringAt":"1970-01-01T00:00:00Z","enable":false}""",
        MINIMUM_VERSION_PARAMETER to DEFAULT_MINIMUM_VERSION,
        LATEST_VERSION_PARAMETER to "",
        UPDATE_URL_PARAMETER to "",
        APP_ICON_PARAMETER to DEFAULT_APP_ICON_VARIANT.toString(),
        WELCOME_MESSAGE_PARAMETER to ""
    )
    private var updateRegistration: ConfigUpdateListenerRegistration? = null
    private var initializationStarted = false
    /** Collection state seen by the previous [updateCrashlyticsCollection] call. */
    private var previousCollectionEnabled: Boolean? = null
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
        previousCollectionEnabled = false
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
                settingsTask.exception?.let { error ->
                    CrashReporter.record(error, "remote_config_step" to "settings")
                }
            }
            remoteConfig.setDefaultsAsync(defaults).addOnCompleteListener { defaultsTask ->
                if (!defaultsTask.isSuccessful) {
                    AppLog.w(TAG, "Failed to apply Remote Config defaults", defaultsTask.exception)
                    defaultsTask.exception?.let { error ->
                        CrashReporter.record(error, "remote_config_step" to "defaults")
                    }
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
                            CrashReporter.record(error, "remote_config_step" to "activate")
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
                CrashReporter.record(error, "remote_config_step" to "fetch")
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

    /**
     * Welcome text: the dedicated `welcome_message` parameter wins, then the
     * token JSON field for configs that still embed the text there. The result
     * may be [WELCOME_MESSAGE_PLACEHOLDER]; callers resolve it with
     * [resolveWelcomeMessage] against the localized bundled string.
     */
    fun getWelcomeMessage(): String {
        val parameter = getString(WELCOME_MESSAGE_PARAMETER)
        if (parameter.isNotBlank()) return parameter
        return getTokenConfig()?.welcomeMessage.orEmpty()
    }

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
        val value = instance.getValue(TOKEN_CONFIG_PARAMETER)
        val source = valueSource(value.source)
        val tokenConfig = getTokenConfig()
        val enabled = crashlyticsCollectionEnabled(value.source, tokenConfig)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
        val reason = when {
            tokenConfig == null -> "'$TOKEN_CONFIG_PARAMETER' is missing or invalid"
            else -> "'$TOKEN_CONFIG_PARAMETER'.enable=${tokenConfig.enable}"
        }
        AppLog.d(TAG) { "Crashlytics collection enabled=$enabled ($reason, source=$source)" }
        // While collection is disabled the SDK persists a crash to disk but parks
        // it behind "Waiting for send/deleteUnsentReports to be called", so a
        // session that crashes inside the disabled window (for example an
        // offline cold start) would never reach Firebase on its own. Flush the
        // parked reports as soon as collection turns back on.
        val wasEnabled = previousCollectionEnabled
        previousCollectionEnabled = enabled
        if (enabled && wasEnabled == false) {
            FirebaseCrashlytics.getInstance().sendUnsentReports()
        }
    }

    private fun valueSource(source: Int): String = when (source) {
        FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "REMOTE"
        FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "DEFAULT"
        else -> "STATIC"
    }

}

internal fun parseAppIconVariant(value: String): Int = value.trim().toIntOrNull()
    ?.takeIf { it in 1..5 } ?: DEFAULT_APP_ICON_VARIANT

/** Invisible characters that [Char.isWhitespace] does not report but which
 * commonly ride along when a placeholder is pasted into the Firebase console
 * or an editor: non-breaking space, narrow non-breaking space, zero-width
 * space, and the byte-order mark. */
private val welcomeInvisibleChars = charArrayOf(
    '\u00A0', // non-breaking space
    '\u202F', // narrow non-breaking space
    '\u200B', // zero-width space
    '\uFEFF'  // byte-order mark
)

/**
 * Decides the Crashlytics collection flag for the current Remote Config view.
 *
 * Fail-open policy: only a fetched REMOTE value that parses into a token
 * config is trusted to disable crash reporting. Bundled defaults (offline cold
 * start, blocked network, key absent from the console), unknown sources, and
 * unparsable remote JSON (a console-side mistake, not an explicit revoke) all
 * keep collection ON, so crash reports are never silently swallowed when
 * connectivity to Firebase is unavailable. Reports persisted while the flag
 * was disabled are flushed later through `sendUnsentReports()`.
 */
internal fun crashlyticsCollectionEnabled(
    valueSource: Int,
    tokenConfig: RemoteTokenConfig?
): Boolean = when {
    valueSource == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE && tokenConfig != null -> tokenConfig.enable
    else -> true
}

/**
 * Resolves the configured welcome text: a blank value or
 * [AircraftRemoteConfig.WELCOME_MESSAGE_PLACEHOLDER] falls back to the
 * localized [localMessage] bundle entry, anything else is shown verbatim.
 *
 * Matching is tolerant of the ways a placeholder can be mangled before it
 * reaches the client: surrounding whitespace, invisible characters, letter
 * case, and occurrences embedded inside longer remote text (those are
 * substituted in place so partial templates still localize).
 */
internal fun resolveWelcomeMessage(configured: String, localMessage: String): String {
    val placeholder = AircraftRemoteConfig.WELCOME_MESSAGE_PLACEHOLDER
    val normalized = configured.filterNot { it in welcomeInvisibleChars }
    if (normalized.isBlank()) return localMessage
    if (normalized.trim().equals(placeholder, ignoreCase = true)) return localMessage
    if (!normalized.contains(placeholder, ignoreCase = true)) return configured
    return replaceIgnoreCase(normalized, placeholder, localMessage)
}

/** Case-insensitive literal replacement of [oldValue] with [newValue]. */
private fun replaceIgnoreCase(text: String, oldValue: String, newValue: String): String {
    var result = text
    var index = result.indexOf(oldValue, ignoreCase = true)
    while (index >= 0) {
        result = result.substring(0, index) + newValue + result.substring(index + oldValue.length)
        index = result.indexOf(oldValue, ignoreCase = true, startIndex = index + newValue.length)
    }
    return result
}

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
