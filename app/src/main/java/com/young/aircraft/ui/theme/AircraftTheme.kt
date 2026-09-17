package com.young.aircraft.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.young.aircraft.data.SettingsRepository
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
val AccentGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val DividerGreen: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val TextBright = Color(0xFFD8E0EF)
val TextBody = Color(0xFFCDD2E0)
val TextSubtle = Color(0xFFAAB4C8)
val TextMuted = Color(0x88FFFFFF)

// Flashlight-originated slots consumed by the app color scheme below.
val FlashSurface = Color(0xFF151A24)
val FlashCritical = Color(0xFFFF6F7E)

internal fun themeAccent(theme: String): Color = when (theme) {
    SettingsRepository.THEME_BLUE -> Color(0xFF64B5FF)
    SettingsRepository.THEME_PURPLE -> Color(0xFFC4A0FF)
    SettingsRepository.THEME_YELLOW -> Color(0xFFFFD54F)
    SettingsRepository.THEME_RED -> Color(0xFFFF5252)
    else -> Color(0xFF00FF88)
}

internal fun aircraftColorScheme(theme: String) = themeAccent(theme).let { accent -> darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF07120D),
    primaryContainer = accent.copy(alpha = 0.18f),
    onPrimaryContainer = accent,
    surface = BackgroundDark,
    surfaceVariant = FlashSurface,
    surfaceContainerHigh = FlashSurface,
    onSurface = TextBright,
    onSurfaceVariant = TextSubtle,
    outline = accent.copy(alpha = 0x44 / 255f),
    error = FlashCritical,
    onError = Color.White
) }

/** Dark tactical surfaces with a persisted, live accent selection. */
@Composable
fun AircraftTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { SettingsRepository(context) }
    var theme by remember(repository) { mutableStateOf(repository.getTheme()) }
    DisposableEffect(repository) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SettingsRepository.KEY_THEME) theme = repository.getTheme()
        }
        repository.registerListener(listener)
        theme = repository.getTheme()
        onDispose { repository.unregisterListener(listener) }
    }
    val colorScheme = remember(theme) { aircraftColorScheme(theme) }
    MaterialTheme(colorScheme = colorScheme, content = content)
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
