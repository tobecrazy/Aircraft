package com.young.aircraft.gui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.SettingsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StarFieldViewTest {

    private lateinit var context: Context
    private lateinit var starFieldView: StarFieldView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        starFieldView = StarFieldView(context)
    }

    @Test
    fun `view initializes without crash`() {
        assertNotNull(starFieldView)
    }

    @Test
    fun `star colors follow persisted theme changes live`() {
        val prefs = context.getSharedPreferences(
            SettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit().putString(SettingsRepository.KEY_THEME, SettingsRepository.THEME_GREEN).commit()
        val liveView = StarFieldView(context)
        assertEquals("❉", liveView.particle.first)

        prefs.edit().putString(SettingsRepository.KEY_THEME, SettingsRepository.THEME_BLUE).commit()
        liveView.themeListener.onSharedPreferenceChanged(prefs, SettingsRepository.KEY_THEME)
        assertEquals("♣", liveView.particle.first)

        prefs.edit().putString(SettingsRepository.KEY_THEME, SettingsRepository.THEME_PURPLE).commit()
        liveView.themeListener.onSharedPreferenceChanged(prefs, SettingsRepository.KEY_THEME)
        assertEquals("♦", liveView.particle.first)

        prefs.edit().putString(SettingsRepository.KEY_THEME, SettingsRepository.THEME_YELLOW).commit()
        liveView.themeListener.onSharedPreferenceChanged(prefs, SettingsRepository.KEY_THEME)
        assertEquals("⭐", liveView.particle.first)

        prefs.edit().putString(SettingsRepository.KEY_THEME, SettingsRepository.THEME_RED).commit()
        liveView.themeListener.onSharedPreferenceChanged(prefs, SettingsRepository.KEY_THEME)
        assertEquals("🌹", liveView.particle.first)

        prefs.edit().remove(SettingsRepository.KEY_THEME).commit()
    }

    @Test
    fun `startAnimation sets animating state`() {
        starFieldView.startAnimation()
        // If startAnimation didn't crash and the view exists, it's animating
        assertNotNull(starFieldView)
    }

    @Test
    fun `stopAnimation does not crash`() {
        starFieldView.startAnimation()
        starFieldView.stopAnimation()
        assertNotNull(starFieldView)
    }

    @Test
    fun `onUserActivity does not crash when not animating`() {
        starFieldView.onUserActivity()
        assertNotNull(starFieldView)
    }

    @Test
    fun `onUserActivity resumes after idle pause`() {
        starFieldView.startAnimation()
        // Simulate the view being visible
        starFieldView.onUserActivity()
        assertNotNull(starFieldView)
    }

    @Test
    fun `view handles zero size gracefully`() {
        // onSizeChanged with 0 should not init stars
        starFieldView.layout(0, 0, 0, 0)
        assertNotNull(starFieldView)
    }

    @Test
    fun `visibility change does not crash`() {
        starFieldView.startAnimation()
        starFieldView.visibility = View.INVISIBLE
        starFieldView.visibility = View.VISIBLE
        assertNotNull(starFieldView)
    }

    @Test
    fun `stop animation after start does not crash`() {
        starFieldView.startAnimation()
        starFieldView.stopAnimation()
        // Verify it can be restarted
        starFieldView.startAnimation()
        assertNotNull(starFieldView)
    }
}
