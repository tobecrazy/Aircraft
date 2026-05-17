package com.young.aircraft.gui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameMode
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.viewmodel.GameViewModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class PuzzleActivity : ComponentActivity() {
    companion object {
        private const val MAX_PUZZLE_LEVEL = 10
        private const val CACHE_PREFS = "puzzle_image_cache"
        private const val KEY_CACHE_FILE = "cached_image_file"
        private const val CACHE_FILE_NAME = "puzzle_cached_image.jpg"
    }

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory(this) }
    private var puzzleLevel: Int = 1
    private var puzzleScore: Long = 0L
    private var totalKills: Int = 0
    private var jetPlaneRes: Int = R.drawable.jet_plane_2
    private var jetPlaneIndex: Int = 0

    private var puzzleImageModel by mutableStateOf<Any?>(null)
    private var isImageLoading by mutableStateOf(true)
    private var imageLoadFailed by mutableStateOf(false)
    private val httpClient: OkHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                        onAllLevelsCleared = { score -> showPuzzleCongratsAndFinish(score) }
                    )
                } else {
                    PuzzleLoadingScreen(
                        isLoading = isImageLoading,
                        hasError = imageLoadFailed,
                        onRetry = {
                            imageLoadFailed = false
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
            val loadedModel = runCatching {
                val prefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE)
                val cachedFileName = prefs.getString(KEY_CACHE_FILE, null)
                val cachedFile = if (cachedFileName.isNullOrBlank()) null else File(cacheDir, cachedFileName)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    return@runCatching Uri.fromFile(cachedFile)
                }

                val request = Request.Builder().url(AircraftConstants.Urls.PEAPIX_BING_CN_FEED).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val latestUrl = AircraftConstants.Urls.extractLatestPuzzleImageUrlFromPeapixFeed(body) ?: return@runCatching null

                val imageRequest = Request.Builder().url(latestUrl).build()
                val imageResponse = httpClient.newCall(imageRequest).execute()
                if (!imageResponse.isSuccessful) return@runCatching null
                val imageBytes = imageResponse.body?.bytes() ?: return@runCatching null
                if (imageBytes.isEmpty()) return@runCatching null

                val file = File(cacheDir, CACHE_FILE_NAME)
                file.outputStream().use { it.write(imageBytes) }
                prefs.edit().putString(KEY_CACHE_FILE, CACHE_FILE_NAME).apply()
                Uri.fromFile(file)
            }
                .getOrNull()

            withContext(Dispatchers.Main) {
                if (loadedModel != null) {
                    puzzleImageModel = loadedModel
                    isImageLoading = false
                } else {
                    imageLoadFailed = true
                    isImageLoading = false
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

@Composable
private fun PuzzleLoadingScreen(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = if (isLoading) "Loading puzzle image..." else "Failed to load puzzle image.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            if (hasError) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Retry")
                }
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
    onAllLevelsCleared: (Long) -> Unit
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
    val solvedTiles = remember(gridSize) { createSolvedTiles(gridSize) }
    var tiles by remember(level, gridSize) { mutableStateOf(shuffleTiles(solvedTiles, gridSize, level)) }

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
        color = MaterialTheme.colorScheme.surface
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Puzzle Level $level / $maxPuzzleLevel",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuzzleStatCard("Moves", moves.toString(), Modifier.weight(1f))
                PuzzleStatCard("Time", formatTime(remainingSec), Modifier.weight(1f))
                PuzzleStatCard("Score", score.toString(), Modifier.weight(1f))
            }

            AsyncImage(
                model = puzzleImageUrl,
                contentDescription = "Puzzle image preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tiles) { tile ->
                    val isEmpty = tile == 0
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isEmpty) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.secondaryContainer
                            )
                            .border(
                                1.dp,
                                if (isEmpty) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isEmpty && solvedState == 0) {
                                val result = moveTile(tiles, tile, gridSize)
                                if (result.moved) {
                                    tiles = result.tiles
                                    moves += 1
                                    score += 10L * level
                                }
                                if (isSolved(tiles)) {
                                    solvedState = 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isEmpty) {
                            val tileIndex = tile - 1
                            val tileRow = tileIndex / gridSize
                            val tileCol = tileIndex % gridSize

                            AsyncImage(
                                model = puzzleImageUrl,
                                contentDescription = "Puzzle tile $tile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = gridSize.toFloat()
                                        scaleY = gridSize.toFloat()
                                        translationX = -size.width * tileCol
                                        translationY = -size.height * tileRow
                                    }
                                    .drawWithContent {
                                        drawContent()
                                    }
                            )

                            Text(
                                text = tile.toString(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onSaveAndExit(level, score) }, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
                Button(
                    enabled = hintsRemaining > 0 && hintVisible == 0 && solvedState == 0,
                    onClick = {
                        hintsRemaining -= 1
                        hintVisible = 1
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hint ($hintsRemaining)")
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
                        contentDescription = "Puzzle hint image",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = "Hint active",
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
                        else "Puzzle Cleared"
                    )
                },
                text = {
                    Text(
                        if (level >= maxPuzzleLevel) stringResource(R.string.hall_of_heroes_message)
                        else "Moves: $moves  Time: ${formatTime(elapsedSec)}"
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
                                tiles = shuffleTiles(solvedTiles, gridSize, level)
                                onProgressSaved(level, score)
                            }
                        }
                    ) {
                        Text(if (level >= maxPuzzleLevel) stringResource(R.string.hall_of_heroes_record_button) else "Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(level, score) }) {
                        Text("Save")
                    }
                }
            )
        }

        if (solvedState == -1) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Time up") },
                text = { Text("Retry this puzzle level") },
                confirmButton = {
                    TextButton(onClick = {
                        moves = 0
                        elapsedSec = 0
                        hintsRemaining = 3
                        hintVisible = 0
                        solvedState = 0
                        tiles = shuffleTiles(solvedTiles, gridSize, level)
                    }) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSaveAndExit(level, score) }) {
                        Text("Save & Exit")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun PuzzleStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

internal data class MoveResult(val tiles: List<Int>, val moved: Boolean)

internal fun gridSizeForDifficulty(difficulty: GameDifficulty): Int {
    val coefficient = when (difficulty) {
        GameDifficulty.EASY -> 0.8f
        GameDifficulty.NORMAL -> 1.0f
        GameDifficulty.HARD -> 1.2f
    }
    return kotlin.math.round(3f * coefficient).toInt().coerceIn(2, 6)
}

internal fun createSolvedTiles(gridSize: Int): List<Int> {
    val list = MutableList(gridSize * gridSize) { it + 1 }
    list[list.lastIndex] = 0
    return list
}

internal fun shuffleTiles(solved: List<Int>, gridSize: Int, level: Int): List<Int> {
    var board = solved
    repeat(gridSize * gridSize * 12 + level * 4) {
        val emptyIndex = board.indexOf(0)
        val neighbors = neighborsOf(emptyIndex, gridSize)
        val swapIndex = neighbors.random()
        board = swap(board, emptyIndex, swapIndex)
    }
    if (isSolved(board)) {
        val emptyIndex = board.indexOf(0)
        val swapIndex = neighborsOf(emptyIndex, gridSize).first()
        board = swap(board, emptyIndex, swapIndex)
    }
    return board
}

internal fun moveTile(tiles: List<Int>, tileValue: Int, gridSize: Int): MoveResult {
    val tileIndex = tiles.indexOf(tileValue)
    val emptyIndex = tiles.indexOf(0)
    if (tileIndex == -1 || emptyIndex == -1) return MoveResult(tiles, false)
    if (tileIndex !in neighborsOf(emptyIndex, gridSize)) return MoveResult(tiles, false)
    return MoveResult(swap(tiles, tileIndex, emptyIndex), true)
}

private fun neighborsOf(index: Int, gridSize: Int): List<Int> {
    val row = index / gridSize
    val col = index % gridSize
    val result = mutableListOf<Int>()
    if (row > 0) result += index - gridSize
    if (row < gridSize - 1) result += index + gridSize
    if (col > 0) result += index - 1
    if (col < gridSize - 1) result += index + 1
    return result
}

private fun swap(tiles: List<Int>, i: Int, j: Int): List<Int> {
    val mutable = tiles.toMutableList()
    val temp = mutable[i]
    mutable[i] = mutable[j]
    mutable[j] = temp
    return mutable
}

internal fun isSolved(tiles: List<Int>): Boolean {
    if (tiles.isEmpty()) return false
    for (i in 0 until tiles.lastIndex) {
        if (tiles[i] != i + 1) return false
    }
    return tiles.last() == 0
}

internal fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mm = safe / 60
    val ss = safe % 60
    return "%02d:%02d".format(mm, ss)
}
