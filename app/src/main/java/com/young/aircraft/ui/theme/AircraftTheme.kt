package com.young.aircraft.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared tactical palette. Every Compose screen must reference these instead of
 * declaring file-local copies; colors used by a single screen only stay local.
 */
val BackgroundDark = Color(0xFF0F1118)
val HeaderBackground = Color(0xFF161A26)
val AccentGreen = Color(0xFF00FF88)
val DividerGreen = Color(0x4400FF88)
val TextBright = Color(0xFFD8E0EF)
val TextBody = Color(0xFFCDD2E0)
val TextSubtle = Color(0xFFAAB4C8)
val TextMuted = Color(0x88FFFFFF)

// Flashlight-originated slots consumed by the app color scheme below.
val FlashSurface = Color(0xFF151A24)
val FlashCritical = Color(0xFFFF6F7E)

val AircraftColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF07120D),
    primaryContainer = AccentGreen.copy(alpha = 0.18f),
    onPrimaryContainer = AccentGreen,
    surface = BackgroundDark,
    surfaceVariant = FlashSurface,
    onSurface = TextBright,
    onSurfaceVariant = TextSubtle,
    outline = DividerGreen,
    error = FlashCritical,
    onError = Color.White
)

/** App-wide theme: the dark tactical scheme shared by every Compose screen. */
@Composable
fun AircraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AircraftColorScheme, content = content)
}

/** Thin green divider under/above screen headers. */
@Composable
fun NeonDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerGreen)
    )
}
