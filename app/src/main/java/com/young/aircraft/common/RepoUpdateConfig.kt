package com.young.aircraft.common

import com.young.aircraft.BuildConfig
import com.young.aircraft.utils.AppLog
import java.util.concurrent.TimeUnit
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal const val REPO_UPDATE_JSON_JSDELIVR_URL =
    "https://cdn.jsdelivr.net/gh/tobecrazy/Aircraft@main/app-update.json"
internal const val REPO_UPDATE_JSON_RAW_URL =
    "https://raw.githubusercontent.com/tobecrazy/Aircraft/main/app-update.json"

/**
 * Version fallback served from this repository's own `app-update.json`.
 *
 * Firebase Remote Config stays the primary source. Whenever Firebase holds no
 * remote value for a key (fetch failed, unreachable, or key absent), the repo
 * JSON — fetched from the CDN at startup — is used instead. A Firebase REMOTE
 * value always wins over the repo file when present.
 */
data class RepoUpdateConfig(
    val minimumVersion: String = "",
    val latestVersion: String = "",
    val updateUrl: String = "",
    // Mirrors the Firebase `AppIcon` parameter (1..5, invalid falls back to 1
    // via parseAppIconVariant, same as the Remote Config logic).
    val appIcon: String = "",
    // Mirrors the token-config welcome gating (showWelcome && non-blank
    // welcomeMessage, same as AircraftApplication.showWelcomeDialog).
    val welcomeMessage: String = "",
    val showWelcome: Boolean = false
)

object RepoUpdateConfigStore {
    private const val TAG = "RepoUpdateConfig"

    @Volatile
    var cached: RepoUpdateConfig? = null
        private set

    @Volatile
    var onUpdated: (() -> Unit)? = null

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun refresh(scope: CoroutineScope) {
        scope.launch { refreshNow() }
    }

    /**
     * Suspend variant of [refresh]: fetches the repo JSON and applies it
     * before returning, so callers (the About screen's manual update check)
     * compare against fallback values published after this process started.
     * Returns the applied config, or null when neither CDN source was
     * reachable (Firebase REMOTE values still take precedence either way).
     */
    suspend fun refreshNow(): RepoUpdateConfig? = withContext(Dispatchers.IO) {
        val config = fetchFirstSuccess()
        if (config != null) {
            cached = config
            AppLog.d(TAG) { "Repo update config applied: $config" }
            onUpdated?.invoke()
        } else {
            AppLog.w(TAG, "Repo update config unreachable; staying on Firebase values")
        }
        config
    }

    private fun fetchFirstSuccess(): RepoUpdateConfig? {
        for (url in listOf(REPO_UPDATE_JSON_JSDELIVR_URL, REPO_UPDATE_JSON_RAW_URL)) {
            val config = runCatching { fetch(url) }.getOrNull()
            if (config != null) return config
            AppLog.w(TAG, "Repo update config fetch failed for $url")
        }
        return null
    }

    private fun fetch(url: String): RepoUpdateConfig? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            if (body.isBlank()) return null
            return parseRepoUpdateConfig(body)
        }
    }

    /** Effective values: Firebase REMOTE wins, repo JSON is the fallback. */
    fun getMinimumVersion(): String =
        firebaseOrRepo(
            AircraftRemoteConfig.MINIMUM_VERSION_PARAMETER,
            AircraftRemoteConfig::getMinimumVersion
        ) { cached?.minimumVersion }

    fun getLatestVersion(): String =
        firebaseOrRepo(
            AircraftRemoteConfig.LATEST_VERSION_PARAMETER,
            AircraftRemoteConfig::getLatestVersion
        ) { cached?.latestVersion }

    fun getUpdateUrl(): String =
        firebaseOrRepo(
            AircraftRemoteConfig.UPDATE_URL_PARAMETER,
            AircraftRemoteConfig::getUpdateUrl
        ) { cached?.updateUrl }

    /** Launcher icon variant: Firebase REMOTE `AppIcon` wins, repo `app_icon`
     * is the fallback, validated exactly like the Remote Config logic. */
    fun getAppIconVariant(): Int {
        if (hasRemoteValue(AircraftRemoteConfig.APP_ICON_PARAMETER)) {
            return AircraftRemoteConfig.getAppIconVariant()
        }
        val repoIcon = cached?.appIcon
        if (!repoIcon.isNullOrBlank()) return parseAppIconVariant(repoIcon)
        return AircraftRemoteConfig.getAppIconVariant()
    }

    /** Welcome message: Firebase token config wins when it enables the
     * welcome dialog, otherwise the repo JSON fallback applies with the same
     * gating (showWelcome && non-blank message). */
    fun getWelcomeMessage(): String {
        val tokenConfig = AircraftRemoteConfig.getTokenConfig()
        if (tokenConfig != null && tokenConfig.showWelcome && tokenConfig.welcomeMessage.isNotBlank()) {
            return tokenConfig.welcomeMessage
        }
        val repo = cached
        if (repo != null && repo.showWelcome && repo.welcomeMessage.isNotBlank()) {
            return repo.welcomeMessage
        }
        return ""
    }

    private inline fun firebaseOrRepo(
        key: String,
        firebaseValue: () -> String,
        repoValue: () -> String?
    ): String {
        if (hasRemoteValue(key)) return firebaseValue()
        return repoValue()?.ifBlank { null } ?: firebaseValue()
    }

    private fun hasRemoteValue(key: String): Boolean = try {
        AircraftRemoteConfig.instance.getValue(key).source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
    } catch (_: IllegalStateException) {
        false
    }

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
        if (compareAppVersions(currentVersion, minimumVersion) == null) return false
        if (compareAppVersions(currentVersion, latestVersion) == null) return false
        return hasOptionalAppUpdate(currentVersion, minimumVersion, latestVersion)
    }
}

internal fun parseRepoUpdateConfig(json: String): RepoUpdateConfig? = try {
    val value = JSONObject(json)
    RepoUpdateConfig(
        minimumVersion = value.optString("minimum_version", ""),
        latestVersion = value.optString("latest_version", ""),
        updateUrl = value.optString("update_url", ""),
        // Accept the generated `app_icon` key as well as the Firebase-canonical
        // `AppIcon` key; optString coerces a numeric literal (3) to "3".
        appIcon = value.optString("app_icon", "").ifBlank { value.optString("AppIcon", "") },
        welcomeMessage = value.optString("welcomeMessage", ""),
        showWelcome = value.optBoolean("showWelcome", false)
    )
} catch (_: Exception) {
    null
}
