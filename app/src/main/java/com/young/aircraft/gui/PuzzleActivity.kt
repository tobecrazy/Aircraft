package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.DividerGreen
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.viewmodel.GameViewModel
import com.young.aircraft.viewmodel.PuzzleImageFailure
import com.young.aircraft.viewmodel.PuzzleImageLoadState
import com.young.aircraft.viewmodel.PuzzleImageUiState
import com.young.aircraft.viewmodel.PuzzleImageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class PuzzleActivity : BaseAircraftActivity() {
    companion object {
        private const val KEY_ACTIVE_PUZZLE_IMAGE_LEVEL = "active_puzzle_image_level"
    }

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory(this) }
    private lateinit var imageViewModel: PuzzleImageViewModel
    private var puzzleLevel: Int = 1
    private var puzzleScore: Long = 0L
    private var totalKills: Int = 0
    private var jetPlaneRes: Int = R.drawable.jet_plane_2
    private var jetPlaneIndex: Int = 0

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        viewModel.getDifficulty()
        puzzleLevel = intent.getIntExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, 1).coerceIn(1, PUZZLE_LEVEL_COUNT)
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
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                val imageState by imageViewModel.uiState.collectAsState()
                if (imageState.sessionStarted) {
                    PuzzleScreen(
                        startLevel = puzzleLevel,
                        startScore = puzzleScore,
                        difficulty = viewModel.getDifficulty(),
                        imageState = imageState,
                        onImageForLevel = imageViewModel::loadLevel,
                        onRetryImage = imageViewModel::retryLevel,
                        onRefreshImage = imageViewModel::refreshLevel,
                        onGuideDismissed = imageViewModel::dismissGuide,
                        onProgressPersisted = ::persistPuzzleProgress,
                        onSaveAndExit = ::saveAndExit,
                        onFinaleRequested = ::finishPuzzleGame
                    )
                } else {
                    PuzzleStartupScreen(
                        load = imageState.load,
                        onRetry = { imageViewModel.retryLevel() }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_PUZZLE_IMAGE_LEVEL, imageViewModel.uiState.value.activeLevel)
    }

    /** Persists the next playable level without finishing the activity. */
    private fun persistPuzzleProgress(nextPlayableLevel: Int, totalScore: Long) {
        lifecycleScope.launch {
            viewModel.savePuzzleData(
                puzzleProgressLevel = nextPlayableLevel,
                puzzleScore = totalScore,
                totalKills = totalKills,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
        }
    }

    private fun saveAndExit(nextPlayableLevel: Int, totalScore: Long) {
        lifecycleScope.launch {
            viewModel.savePuzzleData(
                puzzleProgressLevel = nextPlayableLevel,
                puzzleScore = totalScore,
                totalKills = totalKills,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
            finish()
        }
    }

    private fun finishPuzzleGame(totalScore: Long) {
        lifecycleScope.launch {
            viewModel.savePuzzleData(
                puzzleProgressLevel = PUZZLE_LEVEL_COUNT,
                puzzleScore = totalScore,
                combatLevel = 1,
                totalKills = totalKills,
                jetPlaneResId = jetPlaneRes,
                jetPlaneIndex = jetPlaneIndex
            )
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(AircraftConstants.IntentExtras.PUZZLE_LEVEL, PUZZLE_LEVEL_COUNT)
                    .putExtra(AircraftConstants.IntentExtras.PUZZLE_SCORE, totalScore)
            )
            finish()
        }
    }
}

/** Ten fixed levels; the difficulty only changes the grid size. */
internal const val PUZZLE_LEVEL_COUNT = 10

/** How many scan-intel peeks a player can spend per level. */
internal const val PUZZLE_SCANS_PER_LEVEL = 3

/** How long a scan overlay stays open before closing itself. */
internal const val PUZZLE_SCAN_PEEK_SECONDS = 3

private val PuzzleTileBg = Color(0xFF263142)
private val PuzzleButtonBg = Color(0xFF1F2636)
private val PuzzleTargetBg = Color(0xFF1A2331)
private val PuzzleTrayBg = Color(0xFF101722)
private val PuzzleScanButtonBg: Color
    @Composable get() = AccentGreen
private val PuzzleScanButtonContent = Color(0xFF0A0F18)
private val PuzzlePieceTouchTargetMin = 72.dp
private val PuzzleActionButtonHeight = 52.dp
private const val DRAGGING_PIECE_SCALE = 1.14f

/** One exclusive full-screen overlay at a time; every other state renders in the screen body. */
internal enum class PuzzleOverlay { NONE, GUIDE, SCAN, RESULT, TIMEOUT }

/** Derives which single overlay (if any) should be on screen. */
internal fun puzzleOverlayFor(
    showGuide: Boolean,
    scanActive: Boolean,
    solved: Boolean,
    timedOut: Boolean
): PuzzleOverlay = when {
    scanActive -> PuzzleOverlay.SCAN
    solved -> PuzzleOverlay.RESULT
    timedOut -> PuzzleOverlay.TIMEOUT
    showGuide -> PuzzleOverlay.GUIDE
    else -> PuzzleOverlay.NONE
}

/** User-visible events the puzzle session reacts to. */
internal sealed interface PuzzleSessionEvent {
    data object PieceDropped : PuzzleSessionEvent
    data object Solved : PuzzleSessionEvent
    data object Tick : PuzzleSessionEvent
    data object Expired : PuzzleSessionEvent
    data object ScanRequested : PuzzleSessionEvent
    data object RetryRequested : PuzzleSessionEvent
    data object ContinueRequested : PuzzleSessionEvent
    data class PlacedCountChanged(val count: Int) : PuzzleSessionEvent
}

/**
 * Authoritative state of one puzzle session: current level, accumulated score,
 * round bookkeeping, and the round result. Pure data so transitions are testable.
 */
internal data class PuzzleSessionState(
    val level: Int = 1,
    val totalScore: Long = 0L,
    val moves: Int = 0,
    val elapsedSeconds: Int = 0,
    val scansRemaining: Int = PUZZLE_SCANS_PER_LEVEL,
    val retries: Int = 0,
    val placedCount: Int = 0,
    val roundScore: Long = 0L,
    val roundStars: Int = 0,
    val roundSolved: Boolean = false,
    val roundTimedOut: Boolean = false,
    val boardResetToken: Int = 1
) {
    val scansUsed: Int get() = PUZZLE_SCANS_PER_LEVEL - scansRemaining

    /**
     * Save target for the next playable level: after a non-final win the cleared
     * level must not be stored again (replaying it would award the round score twice).
     */
    val saveTargetLevel: Int
        get() = if (roundSolved && level < PUZZLE_LEVEL_COUNT) level + 1 else level
}

internal fun createInitialPuzzleSession(level: Int, totalScore: Long, gridSize: Int): PuzzleSessionState {
    val safeLevel = level.coerceIn(1, PUZZLE_LEVEL_COUNT)
    return PuzzleSessionState(
        level = safeLevel,
        totalScore = totalScore.coerceAtLeast(0L),
        boardResetToken = safeLevel * 100 + gridSize
    )
}

/**
 * Reducer-style transition for puzzle session events. Solved results are applied
 * at most once per round and later solve events are ignored.
 */
internal fun reducePuzzleSession(
    state: PuzzleSessionState,
    event: PuzzleSessionEvent,
    gridSize: Int,
    levelDurationSeconds: Int
): PuzzleSessionState {
    val roundOver = state.roundSolved || state.roundTimedOut
    return when (event) {
        PuzzleSessionEvent.PieceDropped -> if (roundOver) state else state.copy(moves = state.moves + 1)

        is PuzzleSessionEvent.PlacedCountChanged -> if (roundOver) state else state.copy(placedCount = event.count)

        PuzzleSessionEvent.Solved -> {
            if (roundOver) return state
            val result = calculatePuzzleRoundResult(
                level = state.level,
                gridSize = gridSize,
                moves = state.moves,
                scansUsed = state.scansUsed,
                retries = state.retries,
                remainingSeconds = levelDurationSeconds - state.elapsedSeconds
            )
            state.copy(
                roundScore = result.score,
                roundStars = result.stars,
                roundSolved = true,
                totalScore = state.totalScore + result.score
            )
        }

        PuzzleSessionEvent.Tick ->
            if (roundOver || state.elapsedSeconds >= levelDurationSeconds.coerceAtLeast(0)) {
                state
            } else {
                state.copy(elapsedSeconds = state.elapsedSeconds + 1)
            }

        PuzzleSessionEvent.Expired -> if (roundOver) state else state.copy(roundTimedOut = true)

        PuzzleSessionEvent.ScanRequested -> {
            if (roundOver || state.scansRemaining <= 0) {
                state
            } else {
                state.copy(scansRemaining = state.scansRemaining - 1)
            }
        }

        PuzzleSessionEvent.RetryRequested -> {
            if (!state.roundTimedOut) {
                state
            } else {
                state.copy(
                    retries = state.retries + 1,
                    elapsedSeconds = 0,
                    placedCount = 0,
                    roundTimedOut = false,
                    boardResetToken = state.boardResetToken + 1
                )
            }
        }

        PuzzleSessionEvent.ContinueRequested -> {
            if (!state.roundSolved || state.level >= PUZZLE_LEVEL_COUNT) {
                state
            } else {
                state.copy(
                    level = state.level + 1,
                    moves = 0,
                    elapsedSeconds = 0,
                    scansRemaining = PUZZLE_SCANS_PER_LEVEL,
                    retries = 0,
                    placedCount = 0,
                    roundScore = 0L,
                    roundStars = 0,
                    roundSolved = false,
                    roundTimedOut = false,
                    boardResetToken = state.boardResetToken + 1
                )
            }
        }
    }
}

/** Restores the whole session across rotation, fold changes, and process recreation. */
private val puzzleSessionSaver = listSaver<PuzzleSessionState, Any>(
    save = { state ->
        buildList {
            add(state.level)
            add(state.totalScore)
            add(state.moves)
            add(state.elapsedSeconds)
            add(state.scansRemaining)
            add(state.retries)
            add(state.placedCount)
            add(state.roundScore)
            add(state.roundStars)
            add(state.boardResetToken)
            add(if (state.roundSolved) 1 else 0)
            add(if (state.roundTimedOut) 1 else 0)
        }
    },
    restore = { values ->
        PuzzleSessionState(
            level = values[0] as Int,
            totalScore = values[1] as Long,
            moves = values[2] as Int,
            elapsedSeconds = values[3] as Int,
            scansRemaining = values[4] as Int,
            retries = values[5] as Int,
            placedCount = values[6] as Int,
            roundScore = values[7] as Long,
            roundStars = values[8] as Int,
            boardResetToken = values[9] as Int,
            roundSolved = (values[10] as Int) == 1,
            roundTimedOut = (values[11] as Int) == 1
        )
    }
)

/** Events reported by [PuzzleBoard]; the board knows nothing about scoring or persistence. */
internal sealed interface PuzzleBoardEvent {
    data object PieceDropped : PuzzleBoardEvent
    data class PlacedCountChanged(val count: Int) : PuzzleBoardEvent
    data object Solved : PuzzleBoardEvent
    data class UndoAvailabilityChanged(val available: Boolean) : PuzzleBoardEvent
}

@Composable
private fun PuzzleStartupScreen(
    load: PuzzleImageLoadState,
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
            if (load is PuzzleImageLoadState.Failed) {
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
                    text = puzzleImageFailureMessage(load.category),
                    modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubtle
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PuzzleButtonBg,
                        contentColor = AccentGreen
                    )
                ) {
                    Text(stringResource(R.string.puzzle_retry))
                }
            } else {
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
private fun puzzleImageFailureMessage(category: PuzzleImageFailure): String = when (category) {
    PuzzleImageFailure.NETWORK -> stringResource(R.string.puzzle_failure_network)
    PuzzleImageFailure.FEED_EMPTY -> stringResource(R.string.puzzle_failure_feed)
    PuzzleImageFailure.DOWNLOAD_FAILED -> stringResource(R.string.puzzle_load_failed_hint)
}

@Composable
private fun PuzzleScreen(
    startLevel: Int,
    startScore: Long,
    difficulty: GameDifficulty,
    imageState: PuzzleImageUiState,
    onImageForLevel: (Int) -> Unit,
    onRetryImage: () -> Unit,
    onRefreshImage: () -> Unit,
    onGuideDismissed: () -> Unit,
    onProgressPersisted: (Int, Long) -> Unit,
    onSaveAndExit: (Int, Long) -> Unit,
    onFinaleRequested: (Long) -> Unit
) {
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

    val gridSize = remember(difficulty) { gridSizeForDifficulty(difficulty) }

    // Survive rotation/fold recreation — the board must not reset mid-puzzle
    var session by rememberSaveable(stateSaver = puzzleSessionSaver) {
        mutableStateOf(createInitialPuzzleSession(startLevel, startScore, gridSize))
    }

    var undoRequested by rememberSaveable(session.level, session.boardResetToken) { mutableIntStateOf(0) }
    var canUndo by rememberSaveable(session.level, session.boardResetToken) { mutableStateOf(false) }
    var scanRequestToken by rememberSaveable(session.level) { mutableIntStateOf(0) }
    var scanRemainingSec by rememberSaveable { mutableIntStateOf(0) }

    val activeLoad = imageState.load
    val imageReady = activeLoad is PuzzleImageLoadState.Ready && activeLoad.level == session.level
    val totalSec = remember(session.level) { (GameCoreView.getLevelDurationMs(session.level) / 1000L).toInt() }
    val remainingSec = (totalSec - session.elapsedSeconds).coerceAtLeast(0)
    val roundOver = session.roundSolved || session.roundTimedOut
    val boardResetToken = session.boardResetToken

    fun dispatch(event: PuzzleSessionEvent) {
        session = reducePuzzleSession(session, event, gridSize, totalSec)
    }

    // One image request per level; idempotent when the level is already Ready.
    LaunchedEffect(session.level) {
        onImageForLevel(session.level)
    }

    // The round timer only runs while an image is ready and no overlay stops play.
    LaunchedEffect(
        appActive,
        imageReady,
        session.level,
        session.elapsedSeconds,
        session.roundSolved,
        session.roundTimedOut,
        imageState.showGuide
    ) {
        while (appActive == 1 &&
            imageReady &&
            !session.roundSolved &&
            !session.roundTimedOut &&
            !imageState.showGuide &&
            session.elapsedSeconds < totalSec
        ) {
            delay(1.seconds)
            dispatch(PuzzleSessionEvent.Tick)
        }
    }

    LaunchedEffect(session.elapsedSeconds, session.roundSolved) {
        if (imageReady && !session.roundSolved && session.elapsedSeconds >= totalSec) {
            dispatch(PuzzleSessionEvent.Expired)
        }
    }

    LaunchedEffect(scanRequestToken) {
        if (scanRequestToken > 0 && scanRemainingSec > 0) {
            while (scanRemainingSec > 0) {
                delay(1.seconds)
                scanRemainingSec -= 1
            }
        }
    }

    val overlay = puzzleOverlayFor(
        showGuide = imageState.showGuide,
        scanActive = scanRemainingSec > 0,
        solved = session.roundSolved,
        timedOut = session.roundTimedOut
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Scaffold(
            containerColor = BackgroundDark,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            topBar = { PuzzleTopBarHeader(onBack = { onSaveAndExit(session.saveTargetLevel, session.totalScore) }) }
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
                    level = session.level,
                    maxLevel = PUZZLE_LEVEL_COUNT,
                    score = session.totalScore,
                    remainingSec = remainingSec,
                    moves = session.moves,
                    placedCount = session.placedCount,
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
                    if (imageReady) {
                        PuzzleBoard(
                            imageModel = activeLoad.uri.toString(),
                            gridSize = gridSize,
                            level = session.level,
                            enabled = !roundOver,
                            resetToken = boardResetToken,
                            undoRequest = undoRequested,
                            onBoardEvent = { event ->
                                when (event) {
                                    is PuzzleBoardEvent.UndoAvailabilityChanged -> canUndo = event.available
                                    is PuzzleBoardEvent.PlacedCountChanged ->
                                        dispatch(PuzzleSessionEvent.PlacedCountChanged(event.count))

                                    is PuzzleBoardEvent.PieceDropped -> dispatch(PuzzleSessionEvent.PieceDropped)
                                    is PuzzleBoardEvent.Solved -> dispatch(PuzzleSessionEvent.Solved)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        PuzzleLevelImageStatus(
                            load = activeLoad,
                            onRetry = onRetryImage,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
                }

                if (imageState.showStaleNotice) {
                    PuzzleStaleBanner(onRefresh = onRefreshImage)
                }

                PuzzleActionRow(
                    scanLabel = stringResource(R.string.puzzle_hint_button, session.scansRemaining),
                    scanEnabled = imageReady && !roundOver &&
                        session.scansRemaining > 0 && scanRemainingSec == 0,
                    onScanRequested = {
                        dispatch(PuzzleSessionEvent.ScanRequested)
                        scanRemainingSec = PUZZLE_SCAN_PEEK_SECONDS
                        scanRequestToken += 1
                    },
                    undoEnabled = canUndo && imageReady && !roundOver,
                    onUndoRequested = { undoRequested += 1 },
                    onSaveRequested = { onSaveAndExit(session.saveTargetLevel, session.totalScore) }
                )
            }
        }

        when (overlay) {
            PuzzleOverlay.SCAN -> PuzzleScanOverlay(
                imageUrl = (activeLoad as? PuzzleImageLoadState.Ready)?.uri?.toString(),
                remainingSec = scanRemainingSec,
                onClose = { scanRemainingSec = 0 }
            )

            PuzzleOverlay.GUIDE -> AlertDialog(
                onDismissRequest = onGuideDismissed,
                title = { Text(stringResource(R.string.puzzle_guide_title)) },
                text = { Text(stringResource(R.string.puzzle_guide_message)) },
                confirmButton = {
                    TextButton(onClick = onGuideDismissed) {
                        Text(stringResource(R.string.puzzle_guide_confirm))
                    }
                }
            )

            PuzzleOverlay.RESULT -> AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        if (session.level >= PUZZLE_LEVEL_COUNT) stringResource(R.string.hall_of_heroes_title)
                        else stringResource(R.string.puzzle_cleared_title)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.puzzle_round_stars, session.roundStars))
                        Text(stringResource(R.string.puzzle_cleared_message, session.moves, formatTime(session.elapsedSeconds)))
                        Text(stringResource(R.string.puzzle_result_scans, session.scansUsed))
                        Text(stringResource(R.string.puzzle_result_retries, session.retries))
                        Text(stringResource(R.string.puzzle_round_score, session.roundScore))
                        Text(stringResource(R.string.puzzle_total_score, session.totalScore))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (session.level >= PUZZLE_LEVEL_COUNT) {
                                onFinaleRequested(session.totalScore)
                            } else {
                                val clearedLevel = session.level
                                val nextScore = session.totalScore
                                dispatch(PuzzleSessionEvent.ContinueRequested)
                                canUndo = false
                                scanRemainingSec = 0
                                // Persist the next playable level so reopening cannot rescore the cleared round.
                                onProgressPersisted(clearedLevel + 1, nextScore)
                            }
                        }
                    ) {
                        Text(
                            if (session.level >= PUZZLE_LEVEL_COUNT) stringResource(R.string.hall_of_heroes_record_button)
                            else stringResource(R.string.puzzle_continue)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(session.saveTargetLevel, session.totalScore) }) {
                        Text(stringResource(R.string.puzzle_save))
                    }
                }
            )

            PuzzleOverlay.TIMEOUT -> AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.puzzle_time_up_title)) },
                text = { Text(stringResource(R.string.puzzle_time_up_message)) },
                confirmButton = {
                    TextButton(onClick = { dispatch(PuzzleSessionEvent.RetryRequested) }) {
                        Text(stringResource(R.string.puzzle_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(session.saveTargetLevel, session.totalScore) }) {
                        Text(stringResource(R.string.puzzle_save_and_exit))
                    }
                }
            )

            PuzzleOverlay.NONE -> Unit
        }
    }
}

@Composable
private fun PuzzleActionRow(
    scanLabel: String,
    scanEnabled: Boolean,
    onScanRequested: () -> Unit,
    undoEnabled: Boolean,
    onUndoRequested: () -> Unit,
    onSaveRequested: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scan intel is the primary action; undo and save become compact icon buttons.
        Button(
            onClick = onScanRequested,
            enabled = scanEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = PuzzleScanButtonBg,
                contentColor = PuzzleScanButtonContent
            ),
            modifier = Modifier
                .weight(1.6f)
                .height(PuzzleActionButtonHeight)
        ) {
            Text(
                text = scanLabel,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        IconButton(
            onClick = onUndoRequested,
            enabled = undoEnabled,
            modifier = Modifier
                .size(PuzzleActionButtonHeight)
                .background(PuzzleButtonBg, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_puzzle_undo),
                contentDescription = stringResource(R.string.puzzle_undo_icon_desc),
                tint = AccentGreen
            )
        }

        IconButton(
            onClick = onSaveRequested,
            modifier = Modifier
                .size(PuzzleActionButtonHeight)
                .background(PuzzleButtonBg, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_puzzle_save),
                contentDescription = stringResource(R.string.puzzle_save_icon_desc),
                tint = AccentGreen
            )
        }
    }
}

@Composable
private fun PuzzleStaleBanner(onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = PuzzleButtonBg,
        border = BorderStroke(1.dp, DividerGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.puzzle_image_stale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = TextSubtle
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.puzzle_refresh_image), color = AccentGreen)
            }
        }
    }
}

@Composable
private fun PuzzleScanOverlay(
    imageUrl: Any?,
    remainingSec: Int,
    onClose: () -> Unit
) {
    // Closing early keeps the scan consumed: the cost is paid when the scan starts.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Swallows taps so the board stays blocked while the scan is open.
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.puzzle_hint_image_desc),
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = stringResource(R.string.puzzle_scan_remaining, remainingSec.coerceAtLeast(1)),
                color = AccentGreen,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 12.dp)
                    // Announce countdown changes to assistive technology without stealing focus.
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(
                text = stringResource(R.string.puzzle_hint_active),
                color = TextSubtle,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(stringResource(R.string.puzzle_scan_close), color = Color.White)
            }
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
    onBoardEvent: (PuzzleBoardEvent) -> Unit,
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
        onBoardEvent(PuzzleBoardEvent.UndoAvailabilityChanged(undoStack.isNotEmpty()))
    }

    LaunchedEffect(pieces) {
        onBoardEvent(PuzzleBoardEvent.PlacedCountChanged(pieces.count { it.snapped }))
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
                            onBoardEvent(PuzzleBoardEvent.PieceDropped)
                        }
                        activeMoveStart = null
                        activePieceId = 0
                        if (result.pieces.isNotEmpty() && result.pieces.all { it.snapped }) {
                            onBoardEvent(PuzzleBoardEvent.Solved)
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
    pieceSize: Dp,
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

                // Expose the remaining duration to assistive technology, too; the chip is
                // intentionally non-interactive, so it must not read as a button.
                val timerText = formatTime(remainingSec)
                val timerDescription = stringResource(R.string.puzzle_timer_desc, timerText)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.32f)),
                    modifier = Modifier.semantics {
                        contentDescription = timerDescription
                    }
                ) {
                    Text(
                        text = timerText,
                        color = AccentGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
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
    load: PuzzleImageLoadState,
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
            when (load) {
                is PuzzleImageLoadState.Failed -> {
                    Text(
                        text = stringResource(R.string.puzzle_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = puzzleImageFailureMessage(load.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PuzzleButtonBg,
                            contentColor = AccentGreen
                        )
                    ) {
                        Text(stringResource(R.string.puzzle_retry))
                    }
                }

                else -> {
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
