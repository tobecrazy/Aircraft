package com.young.aircraft.common

import com.young.aircraft.BuildConfig
import com.young.aircraft.utils.AppLog
import java.util.concurrent.TimeUnit
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val updateUrl: String = ""
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
        scope.launch(Dispatchers.IO) {
            val config = fetchFirstSuccess()
            if (config != null) {
                cached = config
                AppLog.d(TAG) { "Repo update config applied: $config" }
                onUpdated?.invoke()
            } else {
                AppLog.w(TAG, "Repo update config unreachable; staying on Firebase values")
            }
        }
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
            val body = response.body?.string().orEmpty()
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
        updateUrl = value.optString("update_url", "")
    )
} catch (_: Exception) {
    null
}
