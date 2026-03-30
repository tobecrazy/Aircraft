package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.providers.SettingsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
        // Reset to defaults
        settingsRepository.setDifficulty(GameDifficulty.NORMAL)
        settingsRepository.setBackgroundSoundEnabled(true)
        settingsRepository.setCombatSoundEnabled(true)
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `selecting difficulty updates repository`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val optionEasy = activity.findViewById<LinearLayout>(R.id.option_easy)
                val optionHard = activity.findViewById<LinearLayout>(R.id.option_hard)

                assertEquals(GameDifficulty.NORMAL, settingsRepository.getDifficulty())

                optionEasy.performClick()
                assertEquals(GameDifficulty.EASY, settingsRepository.getDifficulty())

                optionHard.performClick()
                assertEquals(GameDifficulty.HARD, settingsRepository.getDifficulty())
            }
        }
    }

    @Test
    fun `toggling sound switches updates repository`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val switchBg = activity.findViewById<SwitchCompat>(R.id.switch_bg_sound)
                val switchCombat = activity.findViewById<SwitchCompat>(R.id.switch_combat_sound)

                assertTrue(settingsRepository.isBackgroundSoundEnabled())
                assertTrue(settingsRepository.isCombatSoundEnabled())

                switchBg.isChecked = false
                assertFalse(settingsRepository.isBackgroundSoundEnabled())

                switchCombat.isChecked = false
                assertFalse(settingsRepository.isCombatSoundEnabled())
            }
        }
    }

    @Test
    fun `clicking navigation rows starts correct activities`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rowDeviceInfo = activity.findViewById<LinearLayout>(R.id.row_device_info)
                val rowAbout = activity.findViewById<LinearLayout>(R.id.row_about_aircraft)
                val rowPrivacy = activity.findViewById<LinearLayout>(R.id.row_privacy_policy)

                val shadowActivity = shadowOf(activity)

                rowDeviceInfo.performClick()
                assertEquals(DeviceInfoActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowAbout.performClick()
                assertEquals(AboutAircraftActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowPrivacy.performClick()
                assertEquals(PrivacyPolicyActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)
            }
        }
    }
}
