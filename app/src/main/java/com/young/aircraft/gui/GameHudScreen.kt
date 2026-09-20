package com.young.aircraft.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.young.aircraft.R
import com.young.aircraft.ui.theme.AircraftTheme
import kotlinx.coroutines.delay

/**
 * Observable HUD state shared between MainActivity and the Compose overlay.
 * The game loop itself never touches this: only main-thread Activity callbacks do.
 */
class GameHudState {
    var showTip by mutableStateOf(true)
    var paused by mutableStateOf(false)
}

private val ChipGradientStart = Color(0xF019B565)
private val ChipGradientEnd = Color(0xF0107A4A)
private val ChipStroke = Color(0x9938E08D)
private val ChipTextGreen = Color(0xFF00FF88)
private val HintCardTop = Color(0xE6112E21)
private val HintCardBottom = Color(0xCC07150F)
private val HintCardStroke = Color(0x4D39D17A)
private val MetaChipFill = Color(0x201AFF8A)

/**
 * Fullscreen HUD overlay on top of the GameCoreView surface: top scrim,
 * pause chip, auto-dismissing mission briefing card, and the pause panel.
 */
@Composable
fun GameHudOverlay(
    state: GameHudState,
    sectorChip: String,
    difficultyChip: String,
    airframeChip: String,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xB8020611), Color(0x00020611)))
                )
        )
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 18.dp)) {
            Text(
                text = stringResource(R.string.game_hud_pause),
                color = ChipTextGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    // MaterialButton ignored android:background here, so the original
                    // rendered as plain green text on the TextButton's transparent chip.
                    .clickable(onClick = onPause)
                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 10.dp)
            )
        }
        AnimatedVisibility(
            visible = state.showTip,
            enter = EnterTransition.None,
            exit = fadeOut(tween(280)) + slideOutVertically(tween(280)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 26.dp)
        ) {
            LaunchedEffect(Unit) {
                delay(4200)
                state.showTip = false
            }
            MissionBriefingCard(sectorChip, difficultyChip, airframeChip)
        }
        PauseOverlay(state, accent, onResume, onQuit)
    }
}

@Composable
private fun MissionBriefingCard(sectorChip: String, difficultyChip: String, airframeChip: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(HintCardTop, HintCardBottom)),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, HintCardStroke, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.game_hud_tip_badge),
            color = Color(0xFFF3FFF8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(ChipGradientStart, ChipGradientEnd)),
                    RoundedCornerShape(22.dp)
                )
                .border(1.dp, ChipStroke, RoundedCornerShape(22.dp))
                .padding(start = 10.dp, top = 5.dp, end = 10.dp, bottom = 5.dp)
        )
        Text(
            text = stringResource(R.string.game_hud_tip_title),
            color = Color(0xFFF0FFF7),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.12.em,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.game_hud_tip_subtitle),
            color = Color(0xB8E8D0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
        )
        FlowRow(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetaChip(sectorChip)
            MetaChip(difficultyChip)
            MetaChip(airframeChip)
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        color = Color(0xFFE8FFF2),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(MetaChipFill, RoundedCornerShape(16.dp))
            .border(1.dp, HintCardStroke, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 7.dp)
    )
}

@Composable
private fun PauseOverlay(
    state: GameHudState,
    accent: Color,
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    AnimatedVisibility(
        visible = state.paused,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x9E020611))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Consumes taps like the clickable XML overlay did.
                }
        ) {
            val panelScale by animateFloatAsState(
                targetValue = if (state.paused) 1f else 0.94f,
                animationSpec = tween(220, easing = LinearOutSlowInEasing),
                label = "pausePanelScale"
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        scaleX = panelScale
                        scaleY = panelScale
                    }
                    .shadow(14.dp, RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xF1161A26), Color(0xE40F1118))),
                        RoundedCornerShape(28.dp)
                    )
                    .border(1.dp, accent.copy(alpha = 0x66 / 255f), RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.game_hud_pause_badge),
                    color = Color(0xFFD6FFE9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent, accent.copy(alpha = 0.55f))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .border(1.dp, accent.copy(alpha = 0x99 / 255f), RoundedCornerShape(22.dp))
                        .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp)
                )
                Text(
                    text = stringResource(R.string.pause_title),
                    color = Color(0xFFF4FFF9),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.08.em,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.game_hud_pause_summary),
                    color = Color(0xB8E0C8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                )
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF08130E)
                    ),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pause_resume),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onQuit,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0x4D / 255f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFD8F2E4)
                    ),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pause_quit),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(name = "Game HUD", showBackground = true, backgroundColor = 0xFF020611, widthDp = 411, heightDp = 800)
@Composable
private fun GameHudOverlayPreview() {
    AircraftTheme {
        GameHudOverlay(
            state = remember { GameHudState() },
            sectorChip = "SECTOR 01",
            difficultyChip = "DIFFICULTY NORMAL",
            airframeChip = "AIRFRAME 01",
            accent = ChipTextGreen,
            onPause = {},
            onResume = {},
            onQuit = {}
        )
    }
}

@Preview(name = "Game HUD Paused", showBackground = true, backgroundColor = 0xFF020611, widthDp = 411, heightDp = 800)
@Composable
private fun GameHudOverlayPausedPreview() {
    AircraftTheme {
        GameHudOverlay(
            state = remember {
                GameHudState().apply {
                    showTip = false
                    paused = true
                }
            },
            sectorChip = "SECTOR 01",
            difficultyChip = "DIFFICULTY NORMAL",
            airframeChip = "AIRFRAME 01",
            accent = ChipTextGreen,
            onPause = {},
            onResume = {},
            onQuit = {}
        )
    }
}
