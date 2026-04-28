package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BackgroundDark = Color(0xFF0F1118)
private val HeaderBg = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val DividerGreen = Color(0x4400FF88)
private val TextHint = Color(0x88FFFFFF.toInt())
private val ButtonTextDark = Color(0xFF0F1118)
private val ButtonSecondaryBg = Color(0x16FFFFFF)
private val ButtonSecondaryStroke = Color(0x6600FF88.toInt())
private val DialogGradientTop = Color(0xFF1C2432)
private val DialogGradientBottom = Color(0xFF141D26)
private val DialogTextBody = Color(0xFFD8E0EF)

internal data class SavedGameInfo(val level: Int, val jetIndex: Int, val jetRes: Int)

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {
    private val db by lazy { DatabaseProvider.getDatabase(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val jetPlanes = Aircraft.JET_PLANES
    private var starFieldView: StarFieldView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            MaterialTheme {
                LaunchScreen(
                    onStarFieldCreated = { starFieldView = it }
                )
            }
        }
    }

    internal suspend fun checkForSavedGame(): SavedGameInfo? {
        val playerId = settingsRepository.getOrCreateInstallId()
        val savedData = db.playerGameDataDao().getByPlayerId(playerId)
        val data = savedData.firstOrNull()
        if (data != null && shouldOfferSavedGame(data)) {
            val savedJetIndex = if (data.jetPlaneIndex in Aircraft.JET_PLANES.indices) {
                data.jetPlaneIndex
            } else {
                val foundIndex = Aircraft.JET_PLANES.indexOf(data.jetPlaneRes)
                if (foundIndex != -1) foundIndex else 0
            }
            val savedJetRes = Aircraft.JET_PLANES[savedJetIndex]
            return SavedGameInfo(data.level, savedJetIndex, savedJetRes)
        }
        return null
    }

    internal fun launchGame(level: Int? = null, jetRes: Int, jetIndex: Int) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("jet_plane_res", jetRes)
            .putExtra("jet_plane_index", jetIndex)
        if (level != null) {
            intent.putExtra("start_level", level)
        }
        startActivity(intent)
    }

    internal fun deleteSavedGame() {
        val playerId = settingsRepository.getOrCreateInstallId()
        lifecycleScope.launch {
            db.playerGameDataDao().deleteByPlayerId(playerId)
        }
    }

    private fun shouldOfferSavedGame(data: PlayerGameData): Boolean {
        return data.level > 1 || data.score > 0L
    }

    override fun onDestroy() {
        starFieldView?.stopAnimation()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        starFieldView?.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
private fun LaunchScreen(
    onStarFieldCreated: (StarFieldView) -> Unit
) {
    val activity = LocalContext.current as LaunchActivity
    val jetPlanes = Aircraft.JET_PLANES
    var selectedJetIndex by remember { mutableIntStateOf(0) }
    var savedGameInfo by remember { mutableStateOf<SavedGameInfo?>(null) }
    var showContent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                StarFieldView(ctx).also {
                    it.startAnimation()
                    onStarFieldCreated(it)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("star_field")
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchHeader()
            NeonDivider()

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(400, delayMillis = 100)) +
                        slideInVertically(tween(400, delayMillis = 100)) { -40 }
            ) {
                JetPlaneSelector(
                    jetPlanes = jetPlanes,
                    selectedIndex = selectedJetIndex,
                    onTap = { selectedJetIndex = (selectedJetIndex + 1) % jetPlanes.size }
                )
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(400, delayMillis = 200))
            ) {
                Text(
                    text = stringResource(R.string.launch_jet_hint),
                    color = TextHint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            ActionButtons(
                showContent = showContent,
                onStartGame = {
                    scope.launch {
                        val info = activity.checkForSavedGame()
                        if (info != null) {
                            savedGameInfo = info
                        } else {
                            activity.launchGame(
                                jetRes = jetPlanes[selectedJetIndex],
                                jetIndex = selectedJetIndex
                            )
                        }
                    }
                },
                onHistory = {
                    activity.startActivity(
                        Intent(activity, HistoryActivity::class.java)
                    )
                },
                onSettings = {
                    activity.startActivity(
                        Intent(activity, SettingsActivity::class.java)
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    if (savedGameInfo != null) {
        SavedGameDialog(
            info = savedGameInfo!!,
            onContinue = {
                val info = savedGameInfo!!
                savedGameInfo = null
                activity.launchGame(
                    level = info.level,
                    jetRes = info.jetRes,
                    jetIndex = info.jetIndex
                )
            },
            onNewGame = {
                savedGameInfo = null
                activity.deleteSavedGame()
                activity.launchGame(
                    jetRes = jetPlanes[selectedJetIndex],
                    jetIndex = selectedJetIndex
                )
            },
            onDismiss = { savedGameInfo = null }
        )
    }
}

@Composable
private fun LaunchHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            color = AccentGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 6.sp
        )
    }
}

@Composable
private fun NeonDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerGreen)
    )
}

@Composable
private fun JetPlaneSelector(
    jetPlanes: IntArray,
    selectedIndex: Int,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(130.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
            .testTag("jet_plane_selector"),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.2f))
            },
            label = "jet_plane_anim"
        ) { index ->
            Image(
                painter = painterResource(jetPlanes[index]),
                contentDescription = stringResource(R.string.launch_jet_hint),
                modifier = Modifier.size(110.dp)
            )
        }
    }
}

@Composable
private fun ActionButtons(
    showContent: Boolean,
    onStartGame: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400, delayMillis = 300)) +
                    slideInVertically(tween(400, delayMillis = 300)) { 30 }
        ) {
            TacticalButton(
                text = stringResource(R.string.launch_start_mission),
                isPrimary = true,
                onClick = onStartGame,
                testTag = "btn_start_mission"
            )
        }

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400, delayMillis = 400)) +
                    slideInVertically(tween(400, delayMillis = 400)) { 30 }
        ) {
            TacticalButton(
                text = stringResource(R.string.launch_battle_log),
                isPrimary = false,
                onClick = onHistory,
                testTag = "btn_battle_log"
            )
        }

        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400, delayMillis = 500)) +
                    slideInVertically(tween(400, delayMillis = 500)) { 30 }
        ) {
            TacticalButton(
                text = stringResource(R.string.launch_settings),
                isPrimary = false,
                onClick = onSettings,
                testTag = "btn_settings"
            )
        }
    }
}

@Composable
private fun TacticalButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(46.dp)
            .testTag(testTag),
        shape = shape,
        color = if (isPrimary) AccentGreen else ButtonSecondaryBg,
        border = if (!isPrimary) BorderStroke(1.dp, ButtonSecondaryStroke) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isPrimary) ButtonTextDark else AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SavedGameDialog(
    info: SavedGameInfo,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, DividerGreen),
            color = Color.Transparent,
            modifier = Modifier.testTag("saved_game_dialog")
        ) {
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(DialogGradientTop, DialogGradientBottom)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.continue_game_title).uppercase(),
                    color = AccentGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerGreen)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.continue_game_message, info.level),
                    color = DialogTextBody,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                TacticalButton(
                    text = stringResource(R.string.continue_game_continue).uppercase(),
                    isPrimary = true,
                    onClick = onContinue,
                    testTag = "dialog_btn_continue"
                )

                Spacer(modifier = Modifier.height(10.dp))

                TacticalButton(
                    text = stringResource(R.string.continue_game_new).uppercase(),
                    isPrimary = false,
                    onClick = onNewGame,
                    testTag = "dialog_btn_new_game"
                )
            }
        }
    }
}
