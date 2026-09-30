package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameMode
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.DividerGreen
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.viewmodel.GameViewModel
import com.young.aircraft.viewmodel.PuzzleImageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

class PuzzleActivity : ComponentActivity() {
    companion object {
        private const val MAX_PUZZLE_LEVEL = 10
        private const val KEY_ACTIVE_PUZZLE_IMAGE_LEVEL = "active_puzzle_image_level"
    }

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory(this) }
    private lateinit var imageViewModel: PuzzleImageViewModel
    private var puzzleLevel: Int = 1
    private var puzzleScore: Long = 0L
    private var totalKills: Int = 0
    private var jetPlaneRes: Int = R.drawable.jet_plane_2
    private var jetPlaneIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        puzzleLevel = intent.getIntExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, 1).coerceIn(1, MAX_PUZZLE_LEVEL)
        puzzleScore = intent.getLongExtra(AircraftConstants.IntentExtras.PUZZLE_SCORE, 0L)
        totalKills = intent.getIntExtra(AircraftConstants.IntentExtras.TOTAL_KILLS, 0)
        jetPlaneRes = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_RES, R.drawable.jet_plane_2)
        jetPlaneIndex = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_INDEX, 0)

        // Restore the image level the restored board is actually on (may differ from intent
        // after the user advanced levels) — otherwise recreation shows the wrong level's image
        val activeImageLevel = savedInstanceState?.getInt(KEY_ACTIVE_PUZZLE_IMAGE_LEVEL, puzzleLevel) ?: puzzleLevel
        imageViewModel = ViewModelProvider(
            this,
            PuzzleImageViewModel.Factory(this, activeImageLevel)
        )[PuzzleImageViewModel::class.java]

        setContent {
            AircraftTheme {
                val imageState by imageViewModel.uiState.collectAsState()
                if (imageState.hasStarted) {
                    PuzzleScreen(
                        startLevel = puzzleLevel,
                        startScore = puzzleScore,
                        difficulty = viewModel.getDifficulty(),
                        puzzleImageUrl = imageState.activeImage?.toString(),
                        isImageLoading = imageState.isActiveLoading,
                        imageLoadFailed = imageState.activeImageError != null,
                        imageLoadErrorDetail = imageState.activeImageError,
                        onLevelImageNeeded = imageViewModel::ensureLevelImage,
                        onRetryImage = imageViewModel::retryLevelImage,
                        onSaveAndExit = { level, score ->
                            savePuzzleProgress(level, score, finishAfterSave = true)
                        },
                        onProgressSaved = { level, score -> persistPuzzleProgress(level, score) },
                        onAllLevelsCleared = { score -> showPuzzleCongratsAndFinish(score) },
                        showGuide = imageState.showGuide,
                        onGuideDismiss = imageViewModel::dismissGuide
                    )
                } else {
                    PuzzleLoadingScreen(
                        isLoading = imageState.isActiveLoading,
                        hasError = imageState.activeImageError != null,
                        errorDetail = imageState.activeImageError,
                        onRetry = { imageViewModel.retryLevelImage(imageState.activeImageLevel) }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_PUZZLE_IMAGE_LEVEL, imageViewModel.uiState.value.activeImageLevel)
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

private val PuzzleTileBg = Color(0xFF263142)
private val PuzzleButtonBg = Color(0xFF1F2636)
private val PuzzleTargetBg = Color(0xFF1A2331)
private val PuzzleTrayBg = Color(0xFF101722)
private val PuzzlePieceTouchTargetMin = 72.dp
private const val DRAGGING_PIECE_SCALE = 1.14f

@Composable
private fun PuzzleLoadingScreen(
    isLoading: Boolean,
    hasError: Boolean,
    errorDetail: String?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
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
                    color = AccentGreen,
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
                    color = TextSubtle
                )
                if (!errorDetail.isNullOrBlank()) {
                    Text(
                        text = errorDetail,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PuzzleButtonBg,
                        contentColor = AccentGreen
                    )
                ) {
                    Text(stringResource(R.string.puzzle_retry))
                }
            } else if (isLoading) {
                CircularProgressIndicator(color = AccentGreen)
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
    puzzleImageUrl: String?,
    isImageLoading: Boolean,
    imageLoadFailed: Boolean,
    imageLoadErrorDetail: String?,
    onLevelImageNeeded: (Int) -> Unit,
    onRetryImage: (Int) -> Unit,
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

    // Survive rotation/fold recreation — the board must not reset mid-puzzle
    var level by rememberSaveable { mutableIntStateOf(startLevel.coerceIn(1, maxPuzzleLevel)) }
    var score by rememberSaveable { mutableLongStateOf(startScore) }
    var moves by rememberSaveable(level) { mutableIntStateOf(0) }
    var elapsedSec by rememberSaveable(level) { mutableIntStateOf(0) }
    var hintsRemaining by rememberSaveable(level) { mutableIntStateOf(3) }
    var retries by rememberSaveable(level) { mutableIntStateOf(0) }
    var roundScore by rememberSaveable(level) { mutableLongStateOf(0L) }
    var roundStars by rememberSaveable(level) { mutableIntStateOf(0) }
    var placedCount by rememberSaveable(level) { mutableIntStateOf(0) }
    var hintVisible by remember(level) { mutableIntStateOf(0) }
    var solvedState by rememberSaveable(level) { mutableIntStateOf(0) }

    val gridSize = remember(difficulty) { gridSizeForDifficulty(difficulty) }
    var boardResetToken by rememberSaveable(level, gridSize) { mutableIntStateOf(level * 100 + gridSize) }
    var undoRequested by remember(level, gridSize) { mutableIntStateOf(0) }
    var canUndo by rememberSaveable(level, gridSize) { mutableStateOf(false) }

    val totalSec = remember(level) { (GameCoreView.getLevelDurationMs(level) / 1000L).toInt() }
    val remainingSec = (totalSec - elapsedSec).coerceAtLeast(0)
    val isLevelImageReady = !puzzleImageUrl.isNullOrBlank()

    LaunchedEffect(appActive, solvedState, remainingSec, isLevelImageReady, showGuide) {
        while (appActive == 1 && solvedState == 0 && remainingSec > 0 && isLevelImageReady && !showGuide) {
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
        color = BackgroundDark
    ) {
        Scaffold(
            containerColor = BackgroundDark,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = { PuzzleTopBarHeader(onBack = { onSaveAndExit(level, score) }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .maxContentWidth()
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
                    moves = moves,
                    placedCount = placedCount,
                    totalPieces = gridSize * gridSize
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = HeaderBackground),
                    border = BorderStroke(1.dp, DividerGreen),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (isLevelImageReady) {
                        PuzzleBoard(
                            imageModel = puzzleImageUrl,
                            gridSize = gridSize,
                            level = level,
                            enabled = solvedState == 0,
                            resetToken = boardResetToken,
                            undoRequest = undoRequested,
                            onUndoAvailabilityChanged = { canUndo = it },
                            onPieceDropped = { moves += 1 },
                            onPlacedCountChanged = { placedCount = it },
                            onSolved = {
                                if (solvedState == 0) {
                                    val result = calculatePuzzleRoundResult(level, gridSize, moves + 1, 3 - hintsRemaining, retries, remainingSec)
                                    roundScore = result.score
                                    roundStars = result.stars
                                    score += result.score
                                    solvedState = 1
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        PuzzleLevelImageStatus(
                            isLoading = isImageLoading,
                            hasError = imageLoadFailed,
                            errorDetail = imageLoadErrorDetail,
                            onRetry = { onRetryImage(level) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
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
                        enabled = canUndo && solvedState == 0 && isLevelImageReady,
                        onClick = { undoRequested += 1 },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.puzzle_undo_button))
                    }
                    Button(
                        enabled = hintsRemaining > 0 && hintVisible == 0 && solvedState == 0 && isLevelImageReady,
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

        if (hintVisible == 1 && isLevelImageReady) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.puzzle_round_stars, roundStars))
                        Text(stringResource(R.string.puzzle_cleared_message, moves, formatTime(elapsedSec)))
                        Text(stringResource(R.string.puzzle_round_score, roundScore))
                        Text(stringResource(R.string.puzzle_total_score, score))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                                if (level >= maxPuzzleLevel) {
                                    onAllLevelsCleared(score)
                                } else {
                                val nextLevel = level + 1
                                onLevelImageNeeded(nextLevel)
                                level = nextLevel
                                moves = 0
                                elapsedSec = 0
                                hintsRemaining = 3
                                retries = 0
                                roundScore = 0L
                                roundStars = 0
                                placedCount = 0
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
                        retries += 1
                        elapsedSec = 0
                        placedCount = 0
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
    onPlacedCountChanged: (Int) -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boardSizePx by remember { mutableIntStateOf(0) }
    var boardScale by remember(resetToken) { mutableStateOf(1f) }
    var pieces by rememberSaveable(resetToken, stateSaver = puzzlePiecesSaver) {
        mutableStateOf<List<PuzzlePieceState>>(emptyList())
    }
    var undoStack by rememberSaveable(resetToken, stateSaver = puzzleUndoSaver) {
        mutableStateOf<List<PuzzleMove>>(emptyList())
    }
    var activeMoveStart by remember(resetToken) { mutableStateOf<PuzzlePieceState?>(null) }
    var activePieceId by remember(resetToken) { mutableIntStateOf(0) }
    var playAreaHeightPx by remember { mutableIntStateOf(0) }
    var previousBoardSize by rememberSaveable(resetToken) { mutableIntStateOf(0) }
    var previousPlayAreaHeight by rememberSaveable(resetToken) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val spacingPx = with(density) { 4.dp.toPx() }

    LaunchedEffect(gridSize, level, boardSizePx, playAreaHeightPx, resetToken) {
        if (boardSizePx > 0 && playAreaHeightPx > boardSizePx) {
            if (pieces.isEmpty()) {
                pieces = createPuzzlePieces(gridSize, boardSizePx.toFloat(), level, playAreaHeightPx.toFloat())
            } else if (previousBoardSize > 0 && previousPlayAreaHeight > 0 &&
                (previousBoardSize != boardSizePx || previousPlayAreaHeight != playAreaHeightPx)
            ) {
                val scaleX = boardSizePx.toFloat() / previousBoardSize
                val scaleY = playAreaHeightPx.toFloat() / previousPlayAreaHeight
                pieces = pieces.map { it.copy(x = it.x * scaleX, y = it.y * scaleY) }
                undoStack = undoStack.map { move ->
                    move.copy(previous = move.previous.copy(x = move.previous.x * scaleX, y = move.previous.y * scaleY))
                }
            }
            previousBoardSize = boardSizePx
            previousPlayAreaHeight = playAreaHeightPx
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

    LaunchedEffect(pieces) {
        onPlacedCountChanged(pieces.count { it.snapped })
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSize = if (maxWidth < maxHeight * 0.72f) maxWidth else maxHeight * 0.72f
        val playAreaHeight = maxHeight
        val pieceSize = boardSize / gridSize

        Box(
            modifier = Modifier
                .width(boardSize)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, DividerGreen, RoundedCornerShape(14.dp))
                .onSizeChanged {
                    boardSizePx = it.width
                    playAreaHeightPx = it.height
                }
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
                    color = AccentGreen,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Box(
                modifier = Modifier
                    .size(boardSize)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(PuzzleTargetBg)
                    .border(1.dp, DividerGreen, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
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
                                    DividerGreen.copy(alpha = 0.55f),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playAreaHeight - boardSize)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(PuzzleTrayBg)
                    .border(
                        1.dp,
                        DividerGreen.copy(alpha = 0.3f),
                        RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                    )
            )

            pieces.forEach { piece ->
                PuzzlePiece(
                    piece = piece,
                    imageModel = imageModel,
                    gridSize = gridSize,
                    pieceSize = pieceSize,
                    enabled = enabled && !piece.snapped,
                    isDragging = activePieceId == piece.id,
                    spacingPx = spacingPx,
                    boardScale = boardScale,
                    onDragStart = {
                        activeMoveStart = piece
                        activePieceId = piece.id
                        pieces = bringPuzzlePieceToFront(pieces, piece.id)
                    },
                    onDrag = { dragAmount ->
                        pieces = dragPuzzlePiece(
                            pieces = pieces,
                            pieceId = piece.id,
                            delta = dragAmount / boardScale,
                            boardSizePx = boardSizePx.toFloat(),
                            gridSize = gridSize,
                            playAreaHeightPx = playAreaHeightPx.toFloat()
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
                        activePieceId = 0
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
    isDragging: Boolean,
    spacingPx: Float,
    boardScale: Float,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val targetTint = if (piece.snapped) AccentGreen.copy(alpha = 0.72f) else AccentGreen.copy(alpha = 0.4f)
    val touchTargetSize = if (pieceSize < PuzzlePieceTouchTargetMin) PuzzlePieceTouchTargetMin else pieceSize
    val touchInsetPx = with(LocalDensity.current) { ((touchTargetSize - pieceSize) / 2f).toPx() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (piece.x - touchInsetPx).roundToInt(),
                    (piece.y - touchInsetPx).roundToInt()
                )
            }
            .size(touchTargetSize)
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
        Box(
            modifier = Modifier
                .size(pieceSize)
                .padding(2.dp)
                .graphicsLayer {
                    val scale = when {
                        isDragging -> DRAGGING_PIECE_SCALE
                        enabled -> 1f
                        else -> 0.99f
                    }
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(8.dp))
                .background(PuzzleTileBg)
                .border(1.dp, targetTint, RoundedCornerShape(8.dp)),
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
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun PuzzleTopBarHeader(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.puzzle_game_title),
                color = AccentGreen,
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
                    tint = AccentGreen
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HeaderBackground,
            titleContentColor = AccentGreen,
            navigationIconContentColor = AccentGreen
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
    moves: Int,
    placedCount: Int,
    totalPieces: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HeaderBackground),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, DividerGreen)
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
                        color = TextSubtle
                    )
                }

                AssistChip(
                    onClick = { },
                    label = { Text(text = formatTime(remainingSec), color = AccentGreen) },
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.32f))
                )
            }

            Text(
                stringResource(R.string.puzzle_piece_progress, placedCount, totalPieces),
                style = MaterialTheme.typography.labelMedium,
                color = TextSubtle
            )
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PuzzleTrayBg)
            ) {
                Box(
                    Modifier.fillMaxWidth((placedCount.toFloat() / totalPieces).coerceIn(0f, 1f))
                        .fillMaxHeight().background(AccentGreen)
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
private fun PuzzleLevelImageStatus(
    isLoading: Boolean,
    hasError: Boolean,
    errorDetail: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PuzzleTargetBg)
            .border(1.dp, DividerGreen, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasError) {
                Text(
                    text = stringResource(R.string.puzzle_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (!errorDetail.isNullOrBlank()) {
                    Text(
                        text = errorDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Button(
                    onClick = onRetry,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PuzzleButtonBg,
                        contentColor = AccentGreen
                    )
                ) {
                    Text(stringResource(R.string.puzzle_retry))
                }
            } else if (isLoading) {
                CircularProgressIndicator(color = AccentGreen)
                Text(
                    text = stringResource(R.string.puzzle_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubtle
                )
            }
        }
    }
}

@Composable
private fun PuzzleStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HeaderBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSubtle)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = AccentGreen)
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

private val puzzlePiecesSaver = listSaver<List<PuzzlePieceState>, Int>(
    save = { pieces -> pieces.flatMap { listOf(it.id, it.row, it.col, it.x.toBits(), it.y.toBits(), if (it.snapped) 1 else 0, it.zIndex) } },
    restore = { values -> values.chunked(7).map { PuzzlePieceState(it[0], it[1], it[2], Float.fromBits(it[3]), Float.fromBits(it[4]), it[5] == 1, it[6]) } }
)

private val puzzleUndoSaver = listSaver<List<PuzzleMove>, Int>(
    save = { moves -> moves.flatMap { move -> listOf(move.pieceId, move.previous.id, move.previous.row, move.previous.col, move.previous.x.toBits(), move.previous.y.toBits(), if (move.previous.snapped) 1 else 0, move.previous.zIndex) } },
    restore = { values -> values.chunked(8).map { PuzzleMove(it[0], PuzzlePieceState(it[1], it[2], it[3], Float.fromBits(it[4]), Float.fromBits(it[5]), it[6] == 1, it[7])) } }
)

internal data class PuzzleRoundResult(val score: Long, val stars: Int)

internal fun calculatePuzzleRoundResult(
    level: Int,
    gridSize: Int,
    moves: Int,
    scansUsed: Int,
    retries: Int,
    remainingSeconds: Int
): PuzzleRoundResult {
    val par = gridSize * gridSize
    val score = (100L + level * 25L + remainingSeconds.coerceAtLeast(0) * 2L -
        (moves - par).coerceAtLeast(0) * 5L - scansUsed * 100L - retries * 150L).coerceAtLeast(50L)
    val stars = when {
        retries == 0 && scansUsed == 0 && moves <= par * 1.5f -> 3
        retries == 0 && scansUsed <= 1 && moves <= par * 2 -> 2
        else -> 1
    }
    return PuzzleRoundResult(score, stars)
}

internal fun gridSizeForDifficulty(difficulty: GameDifficulty): Int = when (difficulty) {
    GameDifficulty.EASY -> 3
    GameDifficulty.NORMAL -> 4
    GameDifficulty.HARD -> 5
}

/** Creates a repeatable shuffled tray layout for the current level. */
internal fun createPuzzlePieces(
    gridSize: Int,
    boardSizePx: Float,
    level: Int,
    playAreaHeightPx: Float = boardSizePx
): List<PuzzlePieceState> {
    val pieceSize = boardSizePx / gridSize
    val trayTop = (boardSizePx + pieceSize * 0.16f).coerceAtMost(playAreaHeightPx - pieceSize)
    val trayHeight = (playAreaHeightPx - trayTop).coerceAtLeast(pieceSize)
    val trayColumns = gridSize.coerceAtLeast(1)
    val trayRows = ceil((gridSize * gridSize) / trayColumns.toFloat()).roundToInt().coerceAtLeast(1)
    val horizontalStep = if (trayColumns == 1) 0f else (boardSizePx - pieceSize) / (trayColumns - 1)
    val verticalStep = if (trayRows == 1) 0f else (trayHeight - pieceSize) / (trayRows - 1)
    val trayOrder = (0 until gridSize * gridSize).shuffled(Random(level * 31 + gridSize))
    return List(gridSize * gridSize) { index ->
        val row = index / gridSize
        val col = index % gridSize
        val trayIndex = trayOrder[index]
        val trayCol = trayIndex % trayColumns
        val trayRow = trayIndex / trayColumns
        val rowNudge = if ((trayRow + level) % 2 == 0) pieceSize * 0.08f else -pieceSize * 0.08f
        PuzzlePieceState(
            id = index + 1,
            row = row,
            col = col,
            x = (trayCol * horizontalStep + rowNudge).coerceIn(0f, boardSizePx - pieceSize),
            y = (trayTop + trayRow * verticalStep).coerceIn(0f, playAreaHeightPx - pieceSize),
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
    gridSize: Int,
    playAreaHeightPx: Float = boardSizePx
): List<PuzzlePieceState> {
    val pieceSize = boardSizePx / gridSize
    return pieces.map { piece ->
        if (piece.id == pieceId && !piece.snapped) {
            piece.copy(
                x = (piece.x + delta.x).coerceIn(0f, boardSizePx - pieceSize),
                y = (piece.y + delta.y).coerceIn(0f, playAreaHeightPx - pieceSize)
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
    val snapThreshold = pieceSize * 0.38f
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
