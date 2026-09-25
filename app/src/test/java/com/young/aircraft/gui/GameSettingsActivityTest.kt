package com.young.aircraft.gui

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// Same tall viewport as SettingsActivityTest so every row stays on-screen.
@Config(sdk = [34], qualifiers = "w420dp-h2000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameSettingsActivityTest {

    private lateinit var db: AppDatabase

    @get:Rule(order = 0)
    val dbSetupRule = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            SettingsRepository(context).apply {
                setDifficulty(GameDifficulty.NORMAL)
                setBackgroundSoundEnabled(true)
                setCombatSoundEnabled(true)
                setHitShakeEffectEnabled(true)
            }
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            DatabaseProvider.setDatabase(db)
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<GameSettingsActivity>()

    private fun repository() = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `back button finishes activity`() {
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.history_cancel)
        ).performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `selecting difficulty updates repository`() {
        assertEquals(GameDifficulty.NORMAL, repository().getDifficulty())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.difficulty_easy)).performClick()
        assertEquals(GameDifficulty.EASY, repository().getDifficulty())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.difficulty_hard)).performClick()
        assertEquals(GameDifficulty.HARD, repository().getDifficulty())
    }

    @Test
    fun `toggling sound rows updates repository`() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.background_sound_title)).performClick()
        assertFalse(repository().isBackgroundSoundEnabled())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.combat_sound_title)).performClick()
        assertFalse(repository().isCombatSoundEnabled())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.hit_shake_effect_title)).performClick()
        assertFalse(repository().isHitShakeEffectEnabled())
    }

    @Test
    fun `selecting bgm format updates repository`() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.bgm_format_ogg)).performClick()
        assertEquals(SettingsRepository.BGM_FORMAT_OGG, repository().getBgmFormat())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.bgm_format_mp3)).performClick()
        assertEquals(SettingsRepository.BGM_FORMAT_MP3, repository().getBgmFormat())
    }
}
