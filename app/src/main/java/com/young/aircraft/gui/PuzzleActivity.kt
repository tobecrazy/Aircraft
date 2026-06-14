package com.young.aircraft.gui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameMode
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.viewmodel.GameViewModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit

class PuzzleActivity : ComponentActivity() {
    companion object {
        private const val MAX_PUZZLE_LEVEL = 10
        private const val CACHE_PREFS = "puzzle_image_cache"
        private const val KEY_CACHE_FILE = "cached_image_file"
        private const val CACHE_FILE_NAME = "puzzle_cached_image.jpg"
        private const val TAG = "PuzzleActivity"
        private const val USER_AGENT = "AircraftPuzzle/1.0 (Android)"
    }

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory(this) }
    private var puzzleLevel: Int = 1
    private var puzzleScore: Long = 0L
    private var totalKills: Int = 0
    private var jetPlaneRes: Int = R.drawable.jet_plane_2
    private var jetPlaneIndex: Int = 0
    private lateinit var settingsRepository: SettingsRepository

    private var puzzleImageModel by mutableStateOf<Any?>(null)
    private var isImageLoading by mutableStateOf(true)
    private var imageLoadFailed by mutableStateOf(false)
    private var imageLoadErrorDetail by mutableStateOf<String?>(null)
    private var shouldShowGuide by mutableStateOf(false)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository(this)
        shouldShowGuide = !settingsRepository.isPuzzleGuideCompleted()
        puzzleLevel = intent.getIntExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, 1).coerceIn(1, MAX_PUZZLE_LEVEL)
        puzzleScore = intent.getLongExtra(AircraftConstants.IntentExtras.PUZZLE_SCORE, 0L)
        totalKills = intent.getIntExtra(AircraftConstants.IntentExtras.TOTAL_KILLS, 0)
        jetPlaneRes = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_RES, R.drawable.jet_plane_2)
        jetPlaneIndex = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_INDEX, 0)

        loadPuzzleImageWithCache()

        setContent {
            MaterialTheme {
                if (puzzleImageModel != null) {
                    PuzzleScreen(
                        startLevel = puzzleLevel,
                        startScore = puzzleScore,
                        difficulty = viewModel.getDifficulty(),
                        puzzleImageUrl = puzzleImageModel.toString(),
                        onSaveAndExit = { level, score ->
                            savePuzzleProgress(level, score, finishAfterSave = true)
                        },
                        onProgressSaved = { level, score -> persistPuzzleProgress(level, score) },
                        onAllLevelsCleared = { score -> showPuzzleCongratsAndFinish(score) },
                        showGuide = shouldShowGuide,
                        onGuideDismiss = {
                            shouldShowGuide = false
                            settingsRepository.setPuzzleGuideCompleted(true)
                        }
                    )
                } else {
                    PuzzleLoadingScreen(
                        isLoading = isImageLoading,
                        hasError = imageLoadFailed,
                        errorDetail = imageLoadErrorDetail,
                        onRetry = {
                            imageLoadFailed = false
                            imageLoadErrorDetail = null
                            isImageLoading = true
                            loadPuzzleImageWithCache()
                        }
                    )
                }
            }
        }
    }

    private fun persistPuzzleProgress(level: Int, score: Long) {
        lifecycleScope.launch {
            viewModel.saveGameData(
                level = level,
                totalKills = totalKills,
                puzzleScore = score,
                puzzleLevel = level,
                gameMode = GameMode.PUZZLE,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
        }
    }

    private fun showPuzzleCongratsAndFinish(score: Long) {
        lifecycleScope.launch {
            viewModel.saveGameData(
                level = 1,
                totalKills = totalKills,
                puzzleScore = score,
                puzzleLevel = MAX_PUZZLE_LEVEL,
                gameMode = GameMode.PUZZLE,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, MAX_PUZZLE_LEVEL)
                    .putExtra(AircraftConstants.IntentExtras.PUZZLE_SCORE, score)
            )
            finish()
        }
    }

    private fun loadPuzzleImageWithCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            var failureReason: String? = null
            val loadedModel = runCatching {
                val prefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE)
                val cachedFileName = prefs.getString(KEY_CACHE_FILE, null)
                val cachedFile = if (cachedFileName.isNullOrBlank()) null else File(cacheDir, cachedFileName)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
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
                    response.body?.string().orEmpty()
                }

                val candidates = AircraftConstants.Urls.extractLatestPuzzleImageCandidatesFromPeapixFeed(feedBody)
                if (candidates.isEmpty()) {
                    failureReason = "No image URL in feed"
                    return@runCatching null
                }

                // Try thumbUrl first (~150 KB), then imageUrl, then fullUrl. Most failures
                // were 3.4 MB downloads stalling within OkHttp's default timeouts.
                var imageBytes: ByteArray? = null
                for (candidate in candidates) {
                    val attempt = runCatching {
                        val imageRequest = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "image/jpeg,image/*;q=0.8")
                            .build()
                        httpClient.newCall(imageRequest).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw java.io.IOException("HTTP ${response.code}")
                            }
                            response.body?.bytes()
                        }
                    }
                    val bytes = attempt.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        imageBytes = bytes
                        Log.d(TAG, "Loaded puzzle image from $candidate (${bytes.size} bytes)")
                        break
                    }
                    val cause = attempt.exceptionOrNull()
                    Log.w(TAG, "Failed to fetch $candidate: ${cause?.javaClass?.simpleName}: ${cause?.message}")
                    failureReason = cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "Empty response"
                }

                if (imageBytes == null) {
                    return@runCatching null
                }

                val file = File(cacheDir, CACHE_FILE_NAME)
                file.outputStream().use { it.write(imageBytes) }
                prefs.edit().putString(KEY_CACHE_FILE, CACHE_FILE_NAME).apply()
                failureReason = null
                Uri.fromFile(file)
            }
                .onFailure { throwable ->
                    Log.w(TAG, "Puzzle image load threw", throwable)
                    failureReason = "${throwable.javaClass.simpleName}: ${throwable.message}"
                }
                .getOrNull()

            withContext(Dispatchers.Main) {
                if (loadedModel != null) {
                    puzzleImageModel = loadedModel
                    isImageLoading = false
                    imageLoadFailed = false
                    imageLoadErrorDetail = null
                } else {
                    imageLoadFailed = true
                    isImageLoading = false
                    imageLoadErrorDetail = failureReason
                }
            }
        }
    }

    private fun savePuzzleProgress(level: Int, score: Long, finishAfterSave: Boolean) {
        lifecycleScope.launch {
            viewModel.saveGameData(
                level = level,
                totalKills = totalKills,
                puzzleScore = score,
                puzzleLevel = level,
                gameMode = GameMode.PUZZLE,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
            if (finishAfterSave) {
                finish()
            } else {
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, level)
                        .putExtra(AircraftConstants.IntentExtras.PUZZLE_SCORE, score)
                )
                finish()
            }
        }
    }
}

private val PuzzlePageBg = Color(0xFF0F1118)
private val PuzzlePanelBg = Color(0xFF161A26)
private val PuzzleAccent = Color(0xFF00FF88)
private val PuzzleTextSecondary = Color(0xFFAAB4C8)
private val PuzzleTileBg = Color(0xFF263142)
private val PuzzleDivider = Color(0x4400FF88)
private val PuzzleButtonBg = Color(0xFF1F2636)
private val PuzzleTargetBg = Color(0xFF1A2331)

@Composable
private fun PuzzleLoadingScreen(
    isLoading: Boolean,
    hasError: Boolean,
    errorDetail: String?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PuzzlePageBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasError) {
                Text(
                    text = "⚠",
                    color = PuzzleAccent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.puzzle_load_failed),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.puzzle_load_failed_hint),
                    modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PuzzleTextSecondary
                )
                if (!errorDetail.isNullOrBlank()) {
                    Text(
                        text = errorDetail,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = PuzzleTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PuzzleButtonBg,
                        contentColor = PuzzleAccent
                    )
                ) {
                    Text(stringResource(R.string.puzzle_retry))
                }
            } else if (isLoading) {
                CircularProgressIndicator(color = PuzzleAccent)
                Text(
                    text = stringResource(R.string.puzzle_loading),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PuzzleScreen(
    startLevel: Int,
    startScore: Long,
    difficulty: GameDifficulty,
    puzzleImageUrl: String,
    onSaveAndExit: (Int, Long) -> Unit,
    onProgressSaved: (Int, Long) -> Unit,
    onAllLevelsCleared: (Long) -> Unit,
    showGuide: Boolean,
    onGuideDismiss: () -> Unit
) {
    val maxPuzzleLevel = 10
    val lifecycleOwner = LocalLifecycleOwner.current
    var appActive by remember { mutableIntStateOf(1) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appActive = 1
                Lifecycle.Event.ON_STOP -> appActive = 0
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var level by remember { mutableIntStateOf(startLevel.coerceIn(1, maxPuzzleLevel)) }
    var score by remember { mutableLongStateOf(startScore) }
    var moves by remember(level) { mutableIntStateOf(0) }
    var elapsedSec by remember(level) { mutableIntStateOf(0) }
    var hintsRemaining by remember(level) { mutableIntStateOf(3) }
    var hintVisible by remember(level) { mutableIntStateOf(0) }
    var solvedState by remember(level) { mutableIntStateOf(0) }

    val gridSize = remember(difficulty) { gridSizeForDifficulty(difficulty) }
    var boardResetToken by remember(level, gridSize) { mutableIntStateOf(level * 100 + gridSize) }
    var undoRequested by remember(level, gridSize) { mutableIntStateOf(0) }
    var canUndo by remember(level, gridSize) { mutableStateOf(false) }

    val totalSec = remember(level) { (GameCoreView.getLevelDurationMs(level) / 1000L).toInt() }
    val remainingSec = (totalSec - elapsedSec).coerceAtLeast(0)

    LaunchedEffect(appActive, solvedState, remainingSec) {
        while (appActive == 1 && solvedState == 0 && remainingSec > 0) {
            delay(1000)
            elapsedSec += 1
        }
    }

    LaunchedEffect(remainingSec, solvedState) {
        if (remainingSec == 0 && solvedState == 0) {
            solvedState = -1
        }
    }

    LaunchedEffect(hintVisible) {
        if (hintVisible == 1) {
            delay(3000)
            hintVisible = 0
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PuzzlePageBg
    ) {
        Scaffold(
            containerColor = PuzzlePageBg,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = { PuzzleTopBarHeader(onBack = { onSaveAndExit(level, score) }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PuzzleTopBar(
                    level = level,
                    maxLevel = maxPuzzleLevel,
                    score = score,
                    remainingSec = remainingSec,
                    moves = moves
                )

            AsyncImage(
                model = puzzleImageUrl,
                contentDescription = stringResource(R.string.puzzle_image_preview_desc),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, PuzzleDivider, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = PuzzlePanelBg),
                    border = BorderStroke(1.dp, PuzzleDivider),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    PuzzleBoard(
                        imageModel = puzzleImageUrl,
                        gridSize = gridSize,
                        level = level,
                        enabled = solvedState == 0,
                        resetToken = boardResetToken,
                        undoRequest = undoRequested,
                        onUndoAvailabilityChanged = { canUndo = it },
                        onPieceDropped = {
                            moves += 1
                            score += 10L * level
                        },
                        onSolved = { solvedState = 1 },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onSaveAndExit(level, score) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.puzzle_save))
                    }
                    FilledTonalButton(
                        enabled = canUndo && solvedState == 0,
                        onClick = { undoRequested += 1 },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.puzzle_undo_button))
                    }
                    Button(
                        enabled = hintsRemaining > 0 && hintVisible == 0 && solvedState == 0,
                        onClick = {
                            hintsRemaining -= 1
                            hintVisible = 1
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = PuzzleButtonBg,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.puzzle_hint_button, hintsRemaining))
                    }
                }
            }
        }

        if (hintVisible == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = puzzleImageUrl,
                        contentDescription = stringResource(R.string.puzzle_hint_image_desc),
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = stringResource(R.string.puzzle_hint_active),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                )
            }
        }

        if (solvedState == 1) {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        if (level >= maxPuzzleLevel) stringResource(R.string.hall_of_heroes_title)
                        else stringResource(R.string.puzzle_cleared_title)
                    )
                },
                text = {
                    Text(
                        if (level >= maxPuzzleLevel) stringResource(R.string.hall_of_heroes_message)
                        else stringResource(R.string.puzzle_cleared_message, moves, formatTime(elapsedSec))
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updatedScore = score + remainingSec * 2L
                            if (level >= maxPuzzleLevel) {
                                onAllLevelsCleared(updatedScore)
                            } else {
                                level += 1
                                score = updatedScore
                                moves = 0
                                elapsedSec = 0
                                hintsRemaining = 3
                                hintVisible = 0
                                solvedState = 0
                                boardResetToken += 1
                                onProgressSaved(level, score)
                            }
                        }
                    ) {
                        Text(if (level >= maxPuzzleLevel) stringResource(R.string.hall_of_heroes_record_button) else stringResource(R.string.puzzle_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(level, score) }) {
                        Text(stringResource(R.string.puzzle_save))
                    }
                }
            )
        }

        if (solvedState == -1) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.puzzle_time_up_title)) },
                text = { Text(stringResource(R.string.puzzle_time_up_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        moves = 0
                        elapsedSec = 0
                        hintsRemaining = 3
                        hintVisible = 0
                        solvedState = 0
                        boardResetToken += 1
                    }) {
                        Text(stringResource(R.string.puzzle_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(level, score) }) {
                        Text(stringResource(R.string.puzzle_save_and_exit))
                    }
                }
            )
        }

        if (showGuide) {
            AlertDialog(
                onDismissRequest = onGuideDismiss,
                title = { Text(stringResource(R.string.puzzle_guide_title)) },
                text = { Text(stringResource(R.string.puzzle_guide_message)) },
                confirmButton = {
                    TextButton(onClick = onGuideDismiss) {
                        Text(stringResource(R.string.puzzle_guide_confirm))
                    }
                }
            )
        }
    }
}

@Composable
private fun PuzzleBoard(
    imageModel: Any,
    gridSize: Int,
    level: Int,
    enabled: Boolean,
    resetToken: Int,
    undoRequest: Int,
    onUndoAvailabilityChanged: (Boolean) -> Unit,
    onPieceDropped: () -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boardSizePx by remember { mutableIntStateOf(0) }
    var boardScale by remember(resetToken) { mutableStateOf(1f) }
    var pieces by remember(resetToken) { mutableStateOf(emptyList<PuzzlePieceState>()) }
    var undoStack by remember(resetToken) { mutableStateOf(emptyList<PuzzleMove>()) }
    var activeMoveStart by remember(resetToken) { mutableStateOf<PuzzlePieceState?>(null) }
    val density = LocalDensity.current
    val spacingPx = with(density) { 4.dp.toPx() }

    LaunchedEffect(gridSize, level, boardSizePx, resetToken) {
        if (boardSizePx > 0) {
            pieces = createPuzzlePieces(gridSize, boardSizePx.toFloat(), level)
            undoStack = emptyList()
            boardScale = 1f
        }
    }

    LaunchedEffect(undoRequest) {
        if (undoRequest > 0 && undoStack.isNotEmpty()) {
            val move = undoStack.last()
            pieces = restorePuzzleMove(pieces, move)
            undoStack = undoStack.dropLast(1)
        }
    }

    LaunchedEffect(undoStack) {
        onUndoAvailabilityChanged(undoStack.isNotEmpty())
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSize = if (maxWidth < maxHeight) maxWidth else maxHeight
        val pieceSize = boardSize / gridSize

        Box(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(14.dp))
                .background(PuzzleTargetBg)
                .border(1.dp, PuzzleDivider, RoundedCornerShape(14.dp))
                .onSizeChanged { boardSizePx = minOf(it.width, it.height) }
                .graphicsLayer {
                    scaleX = boardScale
                    scaleY = boardScale
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTransformGestures { _, _, zoom, _ ->
                            boardScale = (boardScale * zoom).coerceIn(0.8f, 2.4f)
                        }
                    }
                }
        ) {
            if (pieces.isEmpty()) {
                CircularProgressIndicator(
                    color = PuzzleAccent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (col * (boardSizePx.toFloat() / gridSize)).roundToInt(),
                                    (row * (boardSizePx.toFloat() / gridSize)).roundToInt()
                                )
                            }
                            .size(pieceSize)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                PuzzleDivider.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
            }

            pieces.forEach { piece ->
                PuzzlePiece(
                    piece = piece,
                    imageModel = imageModel,
                    gridSize = gridSize,
                    pieceSize = pieceSize,
                    enabled = enabled && !piece.snapped,
                    spacingPx = spacingPx,
                    boardScale = boardScale,
                    onDragStart = {
                        activeMoveStart = piece
                        pieces = bringPuzzlePieceToFront(pieces, piece.id)
                    },
                    onDrag = { dragAmount ->
                        pieces = dragPuzzlePiece(
                            pieces = pieces,
                            pieceId = piece.id,
                            delta = dragAmount / boardScale,
                            boardSizePx = boardSizePx.toFloat(),
                            gridSize = gridSize
                        )
                    },
                    onDragEnd = {
                        val before = activeMoveStart
                        val result = snapPuzzlePiece(
                            pieces = pieces,
                            pieceId = piece.id,
                            gridSize = gridSize,
                            boardSizePx = boardSizePx.toFloat()
                        )
                        pieces = result.pieces
                        val after = result.pieces.firstOrNull { it.id == piece.id }
                        if (before != null && after != null && hasPieceMoved(before, after)) {
                            undoStack = undoStack + PuzzleMove(piece.id, before)
                            onPieceDropped()
                        }
                        activeMoveStart = null
                        if (result.pieces.isNotEmpty() && result.pieces.all { it.snapped }) {
                            onSolved()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PuzzlePiece(
    piece: PuzzlePieceState,
    imageModel: Any,
    gridSize: Int,
    pieceSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    spacingPx: Float,
    boardScale: Float,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val targetTint = if (piece.snapped) PuzzleAccent.copy(alpha = 0.72f) else PuzzleAccent.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .offset { IntOffset(piece.x.roundToInt(), piece.y.roundToInt()) }
            .size(pieceSize)
            .padding(2.dp)
            .graphicsLayer { this.scaleX = if (enabled) 1f else 0.99f }
            .clip(RoundedCornerShape(8.dp))
            .background(PuzzleTileBg)
            .border(1.dp, targetTint, RoundedCornerShape(8.dp))
            .pointerInput(piece.id, enabled, boardScale) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragCancel = onDragEnd,
                        onDragEnd = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = stringResource(R.string.puzzle_tile_desc, piece.id),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = gridSize.toFloat()
                    scaleY = gridSize.toFloat()
                    translationX = -size.width * piece.col - spacingPx * piece.col
                    translationY = -size.height * piece.row - spacingPx * piece.row
                }
        )

    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun PuzzleTopBarHeader(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.puzzle_game_title),
                color = PuzzleAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.25.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_header_back),
                    contentDescription = stringResource(R.string.history_back),
                    tint = PuzzleAccent
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PuzzlePanelBg,
            titleContentColor = PuzzleAccent,
            navigationIconContentColor = PuzzleAccent
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    )
}

@Composable
private fun PuzzleTopBar(
    level: Int,
    maxLevel: Int,
    score: Long,
    remainingSec: Int,
    moves: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PuzzlePanelBg),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, PuzzleDivider)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.puzzle_top_bar_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.puzzle_level_progress, level, maxLevel),
                        style = MaterialTheme.typography.bodySmall,
                        color = PuzzleTextSecondary
                    )
                }

                AssistChip(
                    onClick = { },
                    label = { Text(text = formatTime(remainingSec), color = PuzzleAccent) },
                    border = BorderStroke(1.dp, PuzzleAccent.copy(alpha = 0.32f))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PuzzleStatCard(stringResource(R.string.puzzle_stat_moves), moves.toString(), Modifier.weight(1f))
                PuzzleStatCard(stringResource(R.string.puzzle_stat_score), score.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PuzzleStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PuzzlePanelBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = PuzzleTextSecondary)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = PuzzleAccent)
        }
    }
}

internal data class PuzzlePieceState(
    val id: Int,
    val row: Int,
    val col: Int,
    val x: Float,
    val y: Float,
    val snapped: Boolean = false,
    val zIndex: Int = id
)

internal data class PuzzleMove(
    val pieceId: Int,
    val previous: PuzzlePieceState
)

internal fun gridSizeForDifficulty(difficulty: GameDifficulty): Int = when (difficulty) {
    GameDifficulty.EASY -> 3
    GameDifficulty.NORMAL -> 4
    GameDifficulty.HARD -> 5
}

internal fun createPuzzlePieces(gridSize: Int, boardSizePx: Float, level: Int): List<PuzzlePieceState> {
    val pieceSize = boardSizePx / gridSize
    val maxOffset = (pieceSize * 0.42f).coerceAtLeast(16f)
    return List(gridSize * gridSize) { index ->
        val row = index / gridSize
        val col = index % gridSize
        val targetX = col * pieceSize
        val targetY = row * pieceSize
        val horizontalDirection = if ((index + level) % 2 == 0) -1f else 1f
        val verticalDirection = if ((index + level / 2) % 2 == 0) 1f else -1f
        val offsetX = horizontalDirection * (((index % gridSize) + 1) / gridSize.toFloat()) * maxOffset
        val offsetY = verticalDirection * ((((index / gridSize) + 1) / gridSize.toFloat()) * maxOffset)
        PuzzlePieceState(
            id = index + 1,
            row = row,
            col = col,
            x = (targetX + offsetX).coerceIn(0f, boardSizePx - pieceSize),
            y = (targetY + offsetY).coerceIn(0f, boardSizePx - pieceSize),
            snapped = false,
            zIndex = index
        )
    }.let { pieces ->
        if (pieces.all { it.isNearTarget(gridSize, boardSizePx) }) {
            pieces.mapIndexed { index, piece ->
                if (index == pieces.lastIndex) piece.copy(x = 0f, y = 0f) else piece
            }
        } else {
            pieces
        }
    }
}

internal fun dragPuzzlePiece(
    pieces: List<PuzzlePieceState>,
    pieceId: Int,
    delta: Offset,
    boardSizePx: Float,
    gridSize: Int
): List<PuzzlePieceState> {
    val pieceSize = boardSizePx / gridSize
    return pieces.map { piece ->
        if (piece.id == pieceId && !piece.snapped) {
            piece.copy(
                x = (piece.x + delta.x).coerceIn(0f, boardSizePx - pieceSize),
                y = (piece.y + delta.y).coerceIn(0f, boardSizePx - pieceSize)
            )
        } else {
            piece
        }
    }
}

internal data class SnapResult(
    val pieces: List<PuzzlePieceState>,
    val snapped: Boolean
)

internal fun snapPuzzlePiece(
    pieces: List<PuzzlePieceState>,
    pieceId: Int,
    gridSize: Int,
    boardSizePx: Float
): SnapResult {
    var didSnap = false
    val updated = pieces.map { piece ->
        if (piece.id == pieceId && !piece.snapped && piece.isNearTarget(gridSize, boardSizePx)) {
            didSnap = true
            val pieceSize = boardSizePx / gridSize
            piece.copy(
                x = piece.col * pieceSize,
                y = piece.row * pieceSize,
                snapped = true
            )
        } else {
            piece
        }
    }
    return SnapResult(updated, didSnap)
}

internal fun restorePuzzleMove(pieces: List<PuzzlePieceState>, move: PuzzleMove): List<PuzzlePieceState> {
    return pieces.map { piece ->
        if (piece.id == move.pieceId) move.previous else piece
    }
}

internal fun bringPuzzlePieceToFront(pieces: List<PuzzlePieceState>, pieceId: Int): List<PuzzlePieceState> {
    val nextZ = (pieces.maxOfOrNull { it.zIndex } ?: 0) + 1
    return pieces.map { piece ->
        if (piece.id == pieceId) piece.copy(zIndex = nextZ) else piece
    }.sortedBy { it.zIndex }
}

internal fun hasPieceMoved(before: PuzzlePieceState, after: PuzzlePieceState): Boolean {
    return abs(before.x - after.x) > 0.5f ||
        abs(before.y - after.y) > 0.5f ||
        before.snapped != after.snapped
}

private fun PuzzlePieceState.isNearTarget(gridSize: Int, boardSizePx: Float): Boolean {
    val pieceSize = boardSizePx / gridSize
    val snapThreshold = pieceSize * 0.24f
    return abs(x - col * pieceSize) <= snapThreshold &&
        abs(y - row * pieceSize) <= snapThreshold
}

private operator fun Offset.div(value: Float): Offset = Offset(x / value, y / value)

internal fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mm = safe / 60
    val ss = safe % 60
    return "%02d:%02d".format(mm, ss)
}
