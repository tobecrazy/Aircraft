package com.young.aircraft.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PuzzleImageViewModel(
    context: Context,
    initialImageLevel: Int
) : ViewModel() {
    companion object {
        private const val MAX_PUZZLE_LEVEL = 10
        private const val CACHE_PREFS = "puzzle_image_cache"
        private const val KEY_CACHE_FILE_PREFIX = "cached_image_file_"
        private const val CACHE_FILE_NAME_PREFIX = "puzzle_cached_image_level_"
        private const val TAG = "PuzzleActivity"
        private const val USER_AGENT = "AircraftPuzzle/1.0 (Android)"
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
            activeImageLevel = this.initialImageLevel,
            showGuide = !settingsRepository.isPuzzleGuideCompleted()
        )
    )
    val uiState: StateFlow<PuzzleImageUiState> = _uiState.asStateFlow()

    init {
        loadImage(_uiState.value.activeImageLevel)
    }

    fun ensureLevelImage(level: Int) {
        val target = level.coerceIn(1, MAX_PUZZLE_LEVEL)
        _uiState.update { it.copy(activeImageLevel = target) }
        if (target !in _uiState.value.images && target !in _uiState.value.loadingLevels) {
            loadImage(target)
        }
    }

    fun retryLevelImage(level: Int) {
        val target = level.coerceIn(1, MAX_PUZZLE_LEVEL)
        _uiState.update { it.copy(activeImageLevel = target) }
        loadImage(target, forceRefresh = true)
    }

    fun dismissGuide() {
        settingsRepository.setPuzzleGuideCompleted(true)
        _uiState.update { it.copy(showGuide = false) }
    }

    private fun loadImage(level: Int, forceRefresh: Boolean = false) {
        val targetLevel = level.coerceIn(1, MAX_PUZZLE_LEVEL)
        _uiState.update {
            it.copy(
                loadingLevels = it.loadingLevels + targetLevel,
                imageErrors = it.imageErrors - targetLevel
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var failureReason: String? = null
            val loadedUri = runCatching {
                val prefs = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                val cacheKey = cacheKeyForPuzzleLevel(targetLevel)
                val cachedFileName = prefs.getString(cacheKey, null)
                val cachedFile = cachedFileName?.takeIf { it.isNotBlank() }?.let { File(appContext.cacheDir, it) }
                if (!forceRefresh && cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    return@runCatching Uri.fromFile(cachedFile)
                }

                val feedRequest = Request.Builder()
                    .url(AircraftConstants.Urls.PEAPIX_BING_CN_FEED)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                val feedBody = httpClient.newCall(feedRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        failureReason = "Feed HTTP ${response.code}"
                        return@runCatching null
                    }
                    response.body.string().orEmpty()
                }

                val candidateGroups = AircraftConstants.Urls.extractPuzzleImageCandidateGroupsFromPeapixFeed(feedBody)
                val candidates = puzzleImageCandidatesForLevel(candidateGroups, targetLevel)
                if (candidates.isEmpty()) {
                    failureReason = "No unique image URL in feed for puzzle level $targetLevel"
                    return@runCatching null
                }

                // Try thumbnail first, then larger candidates if the download fails.
                var imageBytes: ByteArray? = null
                for (candidate in candidates) {
                    val attempt = runCatching {
                        val imageRequest = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "image/jpeg,image/*;q=0.8")
                            .build()
                        httpClient.newCall(imageRequest).execute().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                            response.body.bytes()
                        }
                    }
                    val bytes = attempt.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        imageBytes = bytes
                        Log.d(TAG, "Loaded puzzle level $targetLevel image from $candidate (${bytes.size} bytes)")
                        break
                    }
                    val cause = attempt.exceptionOrNull()
                    Log.w(TAG, "Failed to fetch $candidate: ${cause?.javaClass?.simpleName}: ${cause?.message}")
                    failureReason = cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "Empty response"
                }

                if (imageBytes == null) return@runCatching null

                val file = File(appContext.cacheDir, cacheFileNameForPuzzleLevel(targetLevel))
                file.outputStream().use { it.write(imageBytes) }
                prefs.edit().putString(cacheKey, file.name).apply()
                failureReason = null
                Uri.fromFile(file)
            }.onFailure { throwable ->
                Log.w(TAG, "Puzzle image load threw", throwable)
                failureReason = "${throwable.javaClass.simpleName}: ${throwable.message}"
            }.getOrNull()

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        images = if (loadedUri == null) state.images else state.images + (targetLevel to loadedUri),
                        loadingLevels = state.loadingLevels - targetLevel,
                        imageErrors = if (loadedUri == null) {
                            state.imageErrors + (targetLevel to (failureReason ?: "Empty response"))
                        } else {
                            state.imageErrors - targetLevel
                        },
                        hasStarted = state.hasStarted || loadedUri != null && targetLevel == initialImageLevel
                    )
                }
            }
        }
    }

    private fun cacheKeyForPuzzleLevel(level: Int): String = "$KEY_CACHE_FILE_PREFIX$level"

    private fun cacheFileNameForPuzzleLevel(level: Int): String = "$CACHE_FILE_NAME_PREFIX$level.jpg"

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

data class PuzzleImageUiState(
    val activeImageLevel: Int,
    val images: Map<Int, Uri> = emptyMap(),
    val loadingLevels: Set<Int> = emptySet(),
    val imageErrors: Map<Int, String> = emptyMap(),
    val hasStarted: Boolean = false,
    val showGuide: Boolean
) {
    val activeImage: Uri? get() = images[activeImageLevel]
    val isActiveLoading: Boolean get() = activeImageLevel in loadingLevels
    val activeImageError: String? get() = imageErrors[activeImageLevel]
}

private fun puzzleImageCandidatesForLevel(candidateGroups: List<List<String>>, level: Int): List<String> {
    if (candidateGroups.isEmpty()) return emptyList()
    val index = ((level - 1) % candidateGroups.size + candidateGroups.size) % candidateGroups.size
    return candidateGroups[index]
}
