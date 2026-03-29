package com.young.aircraft.gui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.providers.SettingsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevelopSettingsActivityTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
        settingsRepository.setInvincibleModeEnabled(false)
        GameStateManager.isInvincible = false
    }

    @Test
    fun `clicking version badge 8 times toggles invincible mode`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val versionBadge = activity.findViewById<android.widget.TextView>(R.id.tv_version_badge)
                
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)

                // Click 7 times - should not toggle yet
                repeat(7) { versionBadge.performClick() }
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)

                // 8th click - should toggle ON
                versionBadge.performClick()
                assertTrue(settingsRepository.isInvincibleModeEnabled())
                assertTrue(GameStateManager.isInvincible)
                assertEquals(
                    context.getString(R.string.invincible_mode_on),
                    ShadowToast.getTextOfLatestToast()
                )

                // Another 8 clicks - should toggle OFF
                repeat(8) { versionBadge.performClick() }
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)
                assertEquals(
                    context.getString(R.string.invincible_mode_off),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<android.view.View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
