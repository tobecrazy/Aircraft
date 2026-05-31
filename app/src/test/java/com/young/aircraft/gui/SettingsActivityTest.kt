package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
        // Reset to defaults
        settingsRepository.setDifficulty(GameDifficulty.NORMAL)
        settingsRepository.setBackgroundSoundEnabled(true)
        settingsRepository.setCombatSoundEnabled(true)
        settingsRepository.setHitShakeEffectEnabled(true)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db)
    }

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.setDatabase(null)
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
                val switchHitShake = activity.findViewById<SwitchCompat>(R.id.switch_hit_shake)

                assertTrue(settingsRepository.isBackgroundSoundEnabled())
                assertTrue(settingsRepository.isCombatSoundEnabled())
                assertTrue(settingsRepository.isHitShakeEffectEnabled())

                switchBg.isChecked = false
                assertFalse(settingsRepository.isBackgroundSoundEnabled())

                switchCombat.isChecked = false
                assertFalse(settingsRepository.isCombatSoundEnabled())

                switchHitShake.isChecked = false
                assertFalse(settingsRepository.isHitShakeEffectEnabled())
            }
        }
    }

    @Test
    fun `clicking navigation rows starts correct activities`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rowDeviceInfo = activity.findViewById<LinearLayout>(R.id.row_device_info)
                val rowFlashlight = activity.findViewById<LinearLayout>(R.id.row_flashlight)
                val rowPuzzleGame = activity.findViewById<LinearLayout>(R.id.row_puzzle_game)
                val rowAbout = activity.findViewById<LinearLayout>(R.id.row_about_aircraft)
                val rowPrivacy = activity.findViewById<LinearLayout>(R.id.row_privacy_policy)

                val shadowActivity = shadowOf(activity)

                rowDeviceInfo.performClick()
                assertEquals(DeviceInfoActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowFlashlight.performClick()
                assertEquals(FlashlightActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowPuzzleGame.performClick()
                assertEquals(PuzzleActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowAbout.performClick()
                assertEquals(AboutAircraftActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

                rowPrivacy.performClick()
                assertEquals(PrivacyPolicyActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)
            }
        }
    }

    @Test
    fun `clicking clear cache row shows confirmation dialog`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val rowClearCache = activity.findViewById<LinearLayout>(R.id.row_clear_cache)

                rowClearCache.performClick()
                Thread.sleep(100)
                shadowOf(Looper.getMainLooper()).idle()

                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull("Clear cache confirmation should be shown", dialog)
                assertTrue(dialog!!.isShowing)
                assertEquals(
                    activity.getString(R.string.clear_cache_badge),
                    dialog.findViewById<TextView>(R.id.dialog_badge)?.text.toString()
                )
                assertEquals(
                    activity.getString(R.string.clear_cache_size_label),
                    dialog.findViewById<TextView>(R.id.stat_label_1)?.text.toString()
                )
                assertTrue(
                    dialog.findViewById<TextView>(R.id.stat_value_1)?.text.toString()
                        .isNotBlank()
                )
                assertEquals(
                    activity.getString(R.string.clear_cache_keep_value),
                    dialog.findViewById<TextView>(R.id.stat_value_2)?.text.toString()
                )
            }
        }
    }
}
