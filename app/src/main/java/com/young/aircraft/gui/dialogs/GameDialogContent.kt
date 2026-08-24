package com.young.aircraft.gui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Visuals lifted from dialog_background / dialog_badge_*_bg / dialog_stat_card*_bg /
// dialog_button_primary* / dialog_button_secondary drawables.
private val DialogGradient = Brush.linearGradient(listOf(Color(0xF11C2432), Color(0xE4141D26)))
private val DialogBorder = Color(0x6600FF88)
private val SecondaryButtonContainer = Color(0x16FFFFFF)
private val SecondaryButtonBorder = Color(0x44FFFFFF)
private val SecondaryButtonText = Color(0xFFD8E0EF)
private val ButtonText = Color(0xFF13221E)
private val MessageColor = Color(0xFFD8E0EF)

/** Per-tone colors; SettingsActivity's clear-cache dialog copies + overrides individual slots. */
data class GameDialogPalette(
    val titleColor: Color,
    val dividerColor: Color,
    val badgeContainer: Color,
    val badgeBorder: Color,
    val statCardContainer: Color,
    val statCardBorder: Color,
    val statLabelColor: Color,
    val positiveButtonContainer: Color
)

val SuccessPalette = GameDialogPalette(
    titleColor = Color(0xFF00FF88),
    dividerColor = Color(0x4400FF88),
    badgeContainer = Color(0x2600FF88),
    badgeBorder = Color(0x6600FF88),
    statCardContainer = Color(0x18FFFFFF),
    statCardBorder = Color(0x22FFFFFF),
    statLabelColor = Color(0x88FFFFFF),
    positiveButtonContainer = Color(0xCCDBFFEF)
)

val DangerPalette = GameDialogPalette(
    titleColor = Color(0xFFFF4444),
    dividerColor = Color(0x44FF4444),
    badgeContainer = Color(0x26FF5555),
    badgeBorder = Color(0x66FF6F7E),
    statCardContainer = Color(0x18FF5555),
    statCardBorder = Color(0x22FF5555),
    statLabelColor = Color(0x88FF6F7E),
    positiveButtonContainer = Color(0xCCFFE0D0)
)

/** One stat card. Overrides fall back to the palette defaults when null. */
data class GameDialogStat(
    val label: String,
    val value: String,
    val labelColor: Color? = null,
    val cardContainer: Color? = null,
    val cardBorder: Color? = null,
    val countUp: Boolean = false
)

@Composable
fun GameDialogContent(
    badgeText: String,
    palette: GameDialogPalette,
    title: String,
    message: String,
    primaryStat: GameDialogStat,
    secondaryStat: GameDialogStat,
    positiveText: String,
    onPositive: () -> Unit,
    negativeText: String? = null,
    onNegative: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DialogGradient, RoundedCornerShape(20.dp))
            .border(1.5.dp, DialogBorder, RoundedCornerShape(20.dp))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = badgeText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(palette.badgeContainer, RoundedCornerShape(999.dp))
                .border(1.dp, palette.badgeBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Text(
            text = title,
            color = palette.titleColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 16.dp)
                .height(1.dp)
                .background(palette.dividerColor)
        )

        Text(
            text = message,
            color = MessageColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(primaryStat, palette, Modifier.weight(1f))
            StatCard(secondaryStat, palette, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (negativeText != null) {
                DialogButton(
                    text = negativeText,
                    textColor = SecondaryButtonText,
                    container = SecondaryButtonContainer,
                    border = SecondaryButtonBorder,
                    onClick = onNegative ?: {},
                    modifier = Modifier.weight(1f)
                )
            }
            DialogButton(
                text = positiveText,
                textColor = ButtonText,
                container = palette.positiveButtonContainer,
                border = Color.Transparent,
                onClick = onPositive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(stat: GameDialogStat, palette: GameDialogPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                stat.cardContainer ?: palette.statCardContainer,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                stat.cardBorder ?: palette.statCardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.label,
            color = stat.labelColor ?: palette.statLabelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = animateIfNeeded(stat),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Counts up from 0 over 800ms (200ms delay), mirroring the legacy ValueAnimator. */
@Composable
private fun animateIfNeeded(stat: GameDialogStat): String {
    val target = stat.value.toIntOrNull()
    if (!stat.countUp || target == null || target <= 0) return stat.value

    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        delay(200)
        animate(0f, target.toFloat(), animationSpec = tween(800)) { value, _ ->
            shown = value.roundToInt()
        }
    }
    return shown.toString()
}

@Composable
private fun DialogButton(
    text: String,
    textColor: Color,
    container: Color,
    border: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(container, RoundedCornerShape(12.dp))
            .then(if (border == Color.Transparent) Modifier else Modifier.border(1.dp, border, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
