package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.SettingsRepository
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
    fun `clicking test crash button throws RuntimeException`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnCrash = activity.findViewById<android.view.View>(R.id.btn_test_crash)
                assertThrows(RuntimeException::class.java) {
                    btnCrash.performClick()
                }
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

    @Test
    fun `android developer assistant tools button opens details activity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<android.view.View>(R.id.btn_android_dev_assistant_tools)
                button.performClick()

                val startedIntent: Intent? = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
                assertNotNull(startedIntent)
                assertEquals(
                    AndroidDevAssistantToolsActivity::class.java.name,
                    startedIntent!!.component?.className
                )
            }
        }
    }

    @Test
    fun `android dev assistant system info action opens device info activity`() {
        val prefs = context.getSharedPreferences("android_dev_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("module_system_info", true).apply()

        ActivityScenario.launch(AndroidDevAssistantToolsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<android.view.View>(R.id.btn_assistant_open_system_info)
                button.performClick()

                val startedIntent: Intent? = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
                assertNotNull(startedIntent)
                assertEquals(
                    DeviceInfoActivity::class.java.name,
                    startedIntent!!.component?.className
                )
            }
        }
    }
}
