package com.young.aircraft.gui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.aircraftColorScheme
import com.young.aircraft.ui.theme.themeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AircraftThemeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `accent resolves per theme and unknown values fall back to green`() {
        assertEquals(Color(0xFF00FF88), themeAccent(context, SettingsRepository.THEME_GREEN))
        assertEquals(Color(0xFF64B5FF), themeAccent(context, SettingsRepository.THEME_BLUE))
        assertEquals(Color(0xFFC4A0FF), themeAccent(context, SettingsRepository.THEME_PURPLE))
        assertEquals(Color(0xFF00FF88), themeAccent(context, "unknown"))
    }

    @Test
    fun `dynamic theme takes its accent from the system scheme, not a fixed neon`() {
        val dynamic = themeAccent(context, SettingsRepository.THEME_DYNAMIC)
        // Robolectric returns a default wallpaper palette; the point is that it is resolved
        // rather than falling through to the green literal.
        assertNotEquals(Color(0xFF00FF88), dynamic)
        assertEquals(dynamic, aircraftColorScheme(context, SettingsRepository.THEME_DYNAMIC).primary)
    }

    @Test
    fun `dialog palette and material surfaces follow all five fixed themes`() {
        listOf(
            SettingsRepository.THEME_GREEN,
            SettingsRepository.THEME_BLUE,
            SettingsRepository.THEME_PURPLE,
            SettingsRepository.THEME_YELLOW,
            SettingsRepository.THEME_RED
        ).forEach { theme ->
            val colors = aircraftColorScheme(context, theme)
            val palette = com.young.aircraft.gui.dialogs.gameDialogPalette(colors.primary)
            assertEquals(themeAccent(context, theme), palette.titleColor)
            assertEquals(colors.primary, palette.positiveButtonContainer)
            assertEquals(colors.primary.copy(alpha = 0.4f), palette.badgeBorder)
            assertEquals(com.young.aircraft.ui.theme.FlashSurface, colors.surfaceContainerHigh)
        }
    }

    @Test
    fun `star field particle follows theme and unknown values fall back to green`() {
        assertEquals("❉" to 0xFF00FF88.toInt(), StarFieldView.particleFor(context, SettingsRepository.THEME_GREEN))
        assertEquals("♣" to 0xFF64B5FF.toInt(), StarFieldView.particleFor(context, SettingsRepository.THEME_BLUE))
        assertEquals("♦" to 0xFFC4A0FF.toInt(), StarFieldView.particleFor(context, SettingsRepository.THEME_PURPLE))
        assertEquals("⭐" to 0xFFFFD54F.toInt(), StarFieldView.particleFor(context, SettingsRepository.THEME_YELLOW))
        assertEquals("🌹" to 0xFFFF5252.toInt(), StarFieldView.particleFor(context, SettingsRepository.THEME_RED))
        assertEquals("❉" to 0xFF00FF88.toInt(), StarFieldView.particleFor(context, "unknown"))
    }

    @Test
    fun `color scheme keeps dark surfaces and only swaps accent with outline alpha`() {
        val green = aircraftColorScheme(context, SettingsRepository.THEME_GREEN)
        val blue = aircraftColorScheme(context, SettingsRepository.THEME_BLUE)

        assertEquals(Color(0xFF0F1118), green.surface)
        assertEquals(Color(0xFF00FF88), green.primary)
        assertEquals(Color(0xFF64B5FF), blue.primary)
        assertEquals(Color(0x4464B5FF), blue.outline)
        assertEquals(green.surface, blue.surface)
        assertEquals(green.onSurface, blue.onSurface)
    }
}
