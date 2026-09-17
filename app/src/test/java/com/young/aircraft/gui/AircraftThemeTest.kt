package com.young.aircraft.gui

import androidx.compose.ui.graphics.Color
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.aircraftColorScheme
import com.young.aircraft.ui.theme.themeAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class AircraftThemeTest {

    @Test
    fun `accent resolves per theme and unknown values fall back to green`() {
        assertEquals(Color(0xFF00FF88), themeAccent(SettingsRepository.THEME_GREEN))
        assertEquals(Color(0xFF64B5FF), themeAccent(SettingsRepository.THEME_BLUE))
        assertEquals(Color(0xFFC4A0FF), themeAccent(SettingsRepository.THEME_PURPLE))
        assertEquals(Color(0xFF00FF88), themeAccent("unknown"))
    }

    @Test
    fun `color scheme keeps dark surfaces and only swaps accent with outline alpha`() {
        val green = aircraftColorScheme(SettingsRepository.THEME_GREEN)
        val blue = aircraftColorScheme(SettingsRepository.THEME_BLUE)

        assertEquals(Color(0xFF0F1118), green.surface)
        assertEquals(Color(0xFF00FF88), green.primary)
        assertEquals(Color(0xFF64B5FF), blue.primary)
        assertEquals(Color(0x4464B5FF), blue.outline)
        assertEquals(green.surface, blue.surface)
        assertEquals(green.onSurface, blue.onSurface)
    }
}
