package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
// Tall viewport keeps most rows on-screen; performScrollTo() in the navigation test
// brings any below-the-fold row into view before clicking (verticalScroll composes all children).
@Config(sdk = [34], qualifiers = "w420dp-h2000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsActivityTest {

    private lateinit var db: AppDatabase

    @get:Rule(order = 0)
    val dbSetupRule = object : ExternalResource() {
        private lateinit var repository: SettingsRepository

        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            repository = SettingsRepository(context)
            repository.setDifficulty(GameDifficulty.NORMAL)
            repository.setBackgroundSoundEnabled(true)
            repository.setCombatSoundEnabled(true)
            repository.setHitShakeEffectEnabled(true)
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            DatabaseProvider.setDatabase(db)
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<SettingsActivity>()

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
        assertTrue(repository().isBackgroundSoundEnabled())

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

    @Test
    fun `clicking navigation rows starts correct activities`() {
        val activity = composeRule.activity
        val cases = mapOf(
            activity.getString(R.string.device_info_title) to DeviceInfoActivity::class.java,
            activity.getString(R.string.flashlight_title) to FlashlightActivity::class.java,
            activity.getString(R.string.puzzle_game_title) to PuzzleActivity::class.java,
            activity.getString(R.string.about_aircraft_title) to AboutAircraftActivity::class.java,
            activity.getString(R.string.privacy_policy_title) to PrivacyPolicyActivity::class.java
        )
        cases.forEach { (rowText, target) ->
            composeRule.onNodeWithText(rowText).performScrollTo().performClick()
            assertEquals(
                "row '$rowText' should start ${target.simpleName}",
                target.name,
                shadowOf(activity).nextStartedActivity.component?.className
            )
        }
    }

    @Test
    fun `clicking clear cache row shows confirmation dialog`() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.clear_cache_title)).performClick()
        // Size computation runs on the IO dispatcher; give it a beat, then drain the main looper.
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Clear cache confirmation should be shown", dialog)
        assertTrue(dialog!!.isShowing)
        // Drain the looper so the dialog's ComposeView attaches and composes.
        repeat(2) { shadowOf(Looper.getMainLooper()).idle() }

        // Dialog content is Compose — assert via its semantics tree.
        val nodes = findAllNodes(
            dialog.window!!.decorView.findSemanticsOwner()!!.rootSemanticsNode
        ).mapNotNull { it.displayText() }

        assertTrue(nodes.contains(composeRule.activity.getString(R.string.clear_cache_badge)))
        val sizeLabelIndex =
            nodes.indexOfFirst { it == composeRule.activity.getString(R.string.clear_cache_size_label) }
        assertTrue(sizeLabelIndex >= 0)
        // The size value is the node right after its label (DFS order).
        assertFalse(nodes.getOrNull(sizeLabelIndex + 1).isNullOrBlank())
        assertTrue(nodes.contains(composeRule.activity.getString(R.string.clear_cache_keep_value)))
    }
}
