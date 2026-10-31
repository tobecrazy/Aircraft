package com.young.aircraft.common

import com.young.aircraft.BuildConfig
import com.young.aircraft.utils.AppLog
import com.young.aircraft.utils.CrashReporter
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
internal val REPO_UPDATE_JSON_URLS = listOf(REPO_UPDATE_JSON_JSDELIVR_URL, REPO_UPDATE_JSON_RAW_URL)

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
    // Mirrors the token-config welcome gating (showWelcome only; the message
    // may be blank or the {{welcome_message}} placeholder so the dialog can
    // fall back to the localized bundled string).
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

    /**
     * Fetches every source instead of stopping at the first success: a stale
     * CDN cache still returns HTTP 200 with valid JSON (just an older
     * `latest_version`), which would otherwise preempt the fallback URL.
     * [selectFreshestRepoUpdateConfig] then keeps the newest copy.
     */
    private fun fetchFirstSuccess(): RepoUpdateConfig? = fetchFirstSuccess(::fetch)

    /** Injectable variant of [fetchFirstSuccess] so unit tests can replay
     * candidate responses per URL without network access; production passes
     * [fetch]. */
    internal fun fetchFirstSuccess(fetch: (String) -> RepoUpdateConfig?): RepoUpdateConfig? {
        val configs = REPO_UPDATE_JSON_URLS.mapNotNull { url ->
            runCatching { fetch(url) }
                .onFailure { error ->
                    CrashReporter.log("Repo update config fetch failed: $url")
                    CrashReporter.record(error, "repo_update_url" to url)
                }
                .getOrNull()
                ?: run {
                    AppLog.w(TAG, "Repo update config fetch failed for $url")
                    null
                }
        }
        return selectFreshestRepoUpdateConfig(configs)
    }

    private fun fetch(url: String): RepoUpdateConfig? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            if (body.isBlank()) {
                CrashReporter.log("Repo update config body is blank: $url")
                return null
            }
            return parseRepoUpdateConfig(body) ?: run {
                CrashReporter.log("Repo update config is malformed: $url")
                null
            }
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

    /**
     * Welcome message: Firebase (the dedicated `welcome_message` parameter or
     * the token config) wins when it enables the welcome dialog, otherwise the
     * repo JSON fallback applies with the same `showWelcome` gating. The text
     * may be [AircraftRemoteConfig.WELCOME_MESSAGE_PLACEHOLDER]; the dialog
     * resolves it to the localized bundled string via `resolveWelcomeMessage`.
     */
    fun getWelcomeMessage(): String {
        val tokenConfig = AircraftRemoteConfig.getTokenConfig()
        if (tokenConfig != null && tokenConfig.showWelcome) {
            return AircraftRemoteConfig.getWelcomeMessage()
        }
        val repo = cached
        if (repo != null && repo.showWelcome) {
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

/**
 * Picks the candidate whose `latest_version` compares highest, so a stale CDN
 * cache (valid JSON carrying an older version) cannot preempt a fresher copy
 * fetched from the fallback URL. Blank or unparsable versions lose against
 * any parsable one; copies that are both unparsable (for example both blank)
 * tie and keep the first fetched.
 */
internal fun selectFreshestRepoUpdateConfig(configs: List<RepoUpdateConfig>): RepoUpdateConfig? =
    configs.maxWithOrNull { left, right ->
        val comparison = compareAppVersions(left.latestVersion, right.latestVersion)
        when {
            comparison != null -> comparison
            // Self-comparison is null only for an unparsable/blank version,
            // which lets us rank the parsable side higher without a
            // dedicated rank type.
            compareAppVersions(right.latestVersion, right.latestVersion) != null -> -1
            compareAppVersions(left.latestVersion, left.latestVersion) != null -> 1
            else -> 0
        }
    }
