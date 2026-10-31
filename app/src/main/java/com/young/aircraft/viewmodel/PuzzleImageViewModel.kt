package com.young.aircraft.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.young.aircraft.utils.AppLog
import com.young.aircraft.utils.CrashReporter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * User-safe failure categories for the active puzzle image load. Raw details
 * (HTTP codes, exception names) stay in [AppLog] and never reach the product UI.
 */
enum class PuzzleImageFailure {
    /** The feed service could not be reached. */
    NETWORK,

    /** The feed answered but contained no usable image URL. */
    FEED_EMPTY,

    /** Candidates existed but every download was rejected. */
    DOWNLOAD_FAILED
}

/** Load state of the active puzzle level's image. */
sealed class PuzzleImageLoadState {
    abstract val level: Int

    /** The request for [level] is in flight; no image is displayable yet. */
    data class Loading(override val level: Int) : PuzzleImageLoadState()

    /** [uri] is a playable local file for [level]; [fromCache] marks cache hits. */
    data class Ready(override val level: Int, val uri: Uri, val fromCache: Boolean) : PuzzleImageLoadState()

    /** The load for [level] failed with a [category] the UI can describe safely. */
    data class Failed(override val level: Int, val category: PuzzleImageFailure) : PuzzleImageLoadState()
}

/** A downloaded puzzle image candidate plus its content type for validation. */
class PuzzleImageDownload(val bytes: ByteArray, val contentType: String?)

data class PuzzleImageUiState(
    val activeLevel: Int,
    val load: PuzzleImageLoadState,
    val sessionStarted: Boolean = false,
    val showGuide: Boolean = false,
    /** Non-blocking notice shown when a refresh failed but a cached image stays playable. */
    val showStaleNotice: Boolean = false
) {
    val isImageReady: Boolean get() = load is PuzzleImageLoadState.Ready
}

class PuzzleImageViewModel internal constructor(
    context: Context,
    initialImageLevel: Int,
    private val ioDispatcher: CoroutineDispatcher,
    /** Fetches the feed body; failing implementations must throw or return a blank body. */
    private val feedLoader: suspend () -> String,
    /** Downloads one candidate URL, returning null or throwing when it cannot be fetched. */
    private val imageDownloader: suspend (String) -> PuzzleImageDownload?
) : ViewModel() {

    constructor(context: Context, initialImageLevel: Int) : this(
        context,
        initialImageLevel,
        Dispatchers.IO,
        ::peapixFeedLoader,
        ::puzzleImageDownloader
    )

    companion object {
        internal const val MAX_PUZZLE_LEVEL = 10
        private const val CACHE_PREFS = "puzzle_image_cache"
        private const val KEY_CACHE_FILE_PREFIX = "cached_image_file_"
        private const val CACHE_FILE_NAME_PREFIX = "puzzle_cached_image_level_"
        private const val TEMP_FILE_NAME_PREFIX = "puzzle_download_level_"
        private const val TAG = "PuzzleActivity"
        private const val USER_AGENT = "AircraftPuzzle/1.0 (Android)"

        /** Small ceiling so a truncated or oversized transfer can never become the cache. */
        const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
    }

    private val appContext = context.applicationContext
    private val initialImageLevel = initialImageLevel.coerceIn(1, MAX_PUZZLE_LEVEL)
    private val settingsRepository = SettingsRepository(appContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _uiState = MutableStateFlow(
        PuzzleImageUiState(
            activeLevel = initialImageLevel,
            load = PuzzleImageLoadState.Loading(initialImageLevel),
            showGuide = !settingsRepository.isPuzzleGuideCompleted()
        )
    )
    val uiState: StateFlow<PuzzleImageUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private val requestGeneration = AtomicInteger(0)

    init {
        loadLevel(initialImageLevel)
    }

    /** Shows [level]'s (possibly cached) image, loading it only when it is not ready yet. */
    fun loadLevel(level: Int) {
        val target = level.coerceIn(1, MAX_PUZZLE_LEVEL)
        if (target != _uiState.value.activeLevel) {
            activeJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    activeLevel = target,
                    load = cachedRead(target) ?: PuzzleImageLoadState.Loading(target),
                    showStaleNotice = false
                )
            }
        }
        val current = _uiState.value.load
        if (current is PuzzleImageLoadState.Ready && current.level == target) return
        // One in-flight request per level: a duplicate trigger must not restart the fetch.
        if (current is PuzzleImageLoadState.Loading && current.level == target && activeJob?.isActive == true) return
        startRequest(target, isRefresh = false)
    }

    /** Re-fetches the active level after a failure; no image is on screen to lose. */
    fun retryLevel() {
        startRequest(_uiState.value.activeLevel, isRefresh = false)
    }

    /**
     * Forces a network reload of the active level. A playable cached image stays on
     * screen for the whole refresh; a failed refresh only raises the stale notice.
     */
    fun refreshLevel() {
        val level = _uiState.value.activeLevel
        val ready = _uiState.value.load is PuzzleImageLoadState.Ready
        startRequest(level, isRefresh = ready)
    }

    fun dismissGuide() {
        settingsRepository.setPuzzleGuideCompleted(true)
        _uiState.update { it.copy(showGuide = false) }
    }

    private fun startRequest(level: Int, isRefresh: Boolean) {
        activeJob?.cancel()
        val generation = requestGeneration.incrementAndGet()
        if (!isRefresh) {
            _uiState.update { state ->
                if (state.load is PuzzleImageLoadState.Ready && state.load.level == level) {
                    state
                } else {
                    state.copy(load = PuzzleImageLoadState.Loading(level), showStaleNotice = false)
                }
            }
        }

        activeJob = viewModelScope.launch(ioDispatcher) {
            val outcome = try {
                loadPuzzleImage(level, forceRefresh = isRefresh)
            } catch (cancelled: CancellationException) {
                // Rethrow only when this request is still the controlling one; a superseded
                // request must never win the race against the newer one.
                if (requestGeneration.get() == generation) throw cancelled
                AppLog.d(TAG) { "Superseded puzzle image request for level $level" }
                null
            } catch (failure: Throwable) {
                AppLog.w(TAG, "Puzzle image load failed", failure)
                CrashReporter.record(failure, "puzzle_level" to level, "refresh" to isRefresh)
                ImageOutcome.Failed(PuzzleImageFailure.DOWNLOAD_FAILED)
            }

            if (outcome != null && requestGeneration.get() == generation) {
                applyOutcome(level, isRefresh, outcome)
            }
        }
    }

    private fun applyOutcome(level: Int, isRefresh: Boolean, outcome: ImageOutcome) {
        _uiState.update { state ->
            when (outcome) {
                is ImageOutcome.Ready -> state.copy(
                    load = PuzzleImageLoadState.Ready(level, outcome.uri, outcome.fromCache),
                    sessionStarted = true,
                    showStaleNotice = false
                )

                is ImageOutcome.Failed -> {
                    val cacheRetained = isRefresh &&
                        state.load is PuzzleImageLoadState.Ready &&
                        state.load.level == level
                    if (cacheRetained) {
                        state.copy(showStaleNotice = true)
                    } else {
                        state.copy(
                            load = PuzzleImageLoadState.Failed(level, outcome.category),
                            showStaleNotice = false
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadPuzzleImage(level: Int, forceRefresh: Boolean): ImageOutcome {
        val prefs = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cacheKey = cacheKeyForPuzzleLevel(level)
        if (!forceRefresh) {
            val cached = validCachedFile(prefs, cacheKey, level)
            if (cached != null) {
                AppLog.d(TAG) { "Using cached puzzle image for level $level from ${cached.name}" }
                return ImageOutcome.Ready(Uri.fromFile(cached), fromCache = true)
            }
        }

        val feedBody = try {
            feedLoader()
        } catch (feedFailure: Throwable) {
            AppLog.w(TAG, "Puzzle image feed request failed", feedFailure)
            CrashReporter.record(feedFailure, "puzzle_level" to level, "puzzle_step" to "feed")
            return ImageOutcome.Failed(PuzzleImageFailure.NETWORK)
        }

        val candidateGroups = AircraftConstants.Urls.extractPuzzleImageCandidateGroupsFromPeapixFeed(feedBody)
        val candidates = puzzleImageCandidatesForLevel(candidateGroups, level)
        if (candidates.isEmpty()) {
            AppLog.w(TAG, "No image URL in puzzle feed for level $level")
            return ImageOutcome.Failed(PuzzleImageFailure.FEED_EMPTY)
        }

        // Thumbnail first, then larger candidates when one download fails.
        val finalFile = File(appContext.cacheDir, cacheFileNameForPuzzleLevel(level))
        val tempFile = File(appContext.cacheDir, "$TEMP_FILE_NAME_PREFIX$level.tmp")
        for (candidate in candidates) {
            val download = try {
                imageDownloader(candidate)
            } catch (downloadFailure: Throwable) {
                AppLog.w(TAG, "Failed to fetch $candidate", downloadFailure)
                CrashReporter.record(downloadFailure, "puzzle_step" to "download", "puzzle_url" to candidate)
                null
            }
            if (download == null || download.bytes.isEmpty()) continue
            val cached = cacheValidatedDownload(download, prefs, cacheKey, tempFile, finalFile, candidate)
            if (cached != null) {
                return ImageOutcome.Ready(Uri.fromFile(cached), fromCache = false)
            }
        }
        tempFile.delete()
        return ImageOutcome.Failed(PuzzleImageFailure.DOWNLOAD_FAILED)
    }

    /** Validates a download and installs it as the level cache atomically. */
    private fun cacheValidatedDownload(
        download: PuzzleImageDownload,
        prefs: SharedPreferences,
        cacheKey: String,
        tempFile: File,
        finalFile: File,
        source: String
    ): File? {
        if (!isUsablePuzzleImage(download)) {
            AppLog.w(TAG, "Rejected puzzle image from $source: contentType=${download.contentType}, bytes=${download.bytes.size}")
            return null
        }
        if (download.bytes.size > MAX_IMAGE_BYTES) {
            AppLog.w(TAG, "Puzzle image from $source exceeds ceiling: ${download.bytes.size} bytes")
            return null
        }
        return runCatching {
            // Write to a temporary file first; only a complete image is renamed into place.
            tempFile.outputStream().use { output -> output.write(download.bytes) }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                if (finalFile.exists() && finalFile.length() > 0) finalFile else null
            } else {
                prefs.edit().putString(cacheKey, finalFile.name).apply()
                finalFile
            }
        }.onFailure { failure ->
            AppLog.w(TAG, "Failed to cache puzzle image from $source", failure)
            CrashReporter.record(failure, "puzzle_step" to "cache", "puzzle_url" to source)
            tempFile.delete()
        }.getOrNull()
    }

    /** Synchronous cache peek used to show saved levels without a spinner. */
    private fun cachedRead(level: Int): PuzzleImageLoadState.Ready? {
        val prefs = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cached = validCachedFile(prefs, cacheKeyForPuzzleLevel(level), level) ?: return null
        return PuzzleImageLoadState.Ready(level, Uri.fromFile(cached), fromCache = true)
    }

    private fun validCachedFile(prefs: SharedPreferences, cacheKey: String, level: Int): File? {
        val name = prefs.getString(cacheKey, null)?.takeIf { it.isNotBlank() } ?: return null
        val file = File(appContext.cacheDir, name)
        if (!file.exists() || file.length() == 0L) {
            AppLog.d(TAG) { "Discarding invalid puzzle cache entry for level $level" }
            prefs.edit().remove(cacheKey).apply()
            if (file.length() == 0L && file.exists()) file.delete()
            return null
        }
        return file
    }

    private fun isUsablePuzzleImage(download: PuzzleImageDownload): Boolean {
        val contentType = download.contentType?.substringBefore(';')?.trim().orEmpty()
        if (contentType.startsWith("image/")) return true
        return hasImageMagicBytes(download.bytes)
    }

    private fun hasImageMagicBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isWebP = bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        val isGif = bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        return isJpeg || isPng || isWebP || isGif
    }

    private fun cacheKeyForPuzzleLevel(level: Int): String = "$KEY_CACHE_FILE_PREFIX$level"

    private fun cacheFileNameForPuzzleLevel(level: Int): String = "$CACHE_FILE_NAME_PREFIX$level.jpg"

    sealed interface ImageOutcome {
        data class Ready(val uri: Uri, val fromCache: Boolean) : ImageOutcome
        data class Failed(val category: PuzzleImageFailure) : ImageOutcome
    }

    class Factory(
        context: Context,
        private val initialImageLevel: Int
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PuzzleImageViewModel(appContext, initialImageLevel) as T
    }
}

/** Default OkHttp feed fetch, running inside the injected puzzle dispatcher. */
private suspend fun peapixFeedLoader(): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(AircraftConstants.Urls.PEAPIX_BING_CN_FEED)
        .header("User-Agent", "AircraftPuzzle/1.0 (Android)")
        .header("Accept", "application/json")
        .build()
    PuzzleHttpClientHolder.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            CrashReporter.log("Puzzle feed responded HTTP ${response.code}")
            throw IOException("Feed HTTP ${response.code}")
        }
        response.body.string().orEmpty()
    }
}

/**
 * Default candidate downloader. Reads with a hard byte ceiling so oversized or
 * truncated responses are rejected before any bytes reach the cache file.
 */
private suspend fun puzzleImageDownloader(url: String): PuzzleImageDownload = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "AircraftPuzzle/1.0 (Android)")
        .header("Accept", "image/jpeg,image/*;q=0.8")
        .build()
    PuzzleHttpClientHolder.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            CrashReporter.log("Puzzle image responded HTTP ${response.code}: $url")
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body
        val contentLength = body.contentLength()
        if (contentLength > PuzzleImageViewModel.MAX_IMAGE_BYTES) {
            CrashReporter.log("Puzzle image exceeds size limit: $contentLength bytes from $url")
            throw IOException("Image exceeds size limit: $contentLength bytes")
        }
        val buffer = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val chunk = ByteArray(FILE_COPY_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > PuzzleImageViewModel.MAX_IMAGE_BYTES) {
                    CrashReporter.log("Puzzle image stream exceeds size limit: $total bytes from $url")
                    throw IOException("Image exceeds size limit: $total bytes read")
                }
                buffer.write(chunk, 0, read)
            }
        }
        PuzzleImageDownload(buffer.toByteArray(), response.header("Content-Type"))
    }
}

/** Shared client so both default hooks keep the same call timeouts. */
private object PuzzleHttpClientHolder {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private const val FILE_COPY_BUFFER_SIZE = 8192

private fun puzzleImageCandidatesForLevel(candidateGroups: List<List<String>>, level: Int): List<String> {
    if (candidateGroups.isEmpty()) return emptyList()
    val index = ((level - 1) % candidateGroups.size + candidateGroups.size) % candidateGroups.size
    return candidateGroups[index]
}
