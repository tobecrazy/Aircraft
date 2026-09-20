package com.young.aircraft.gui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.GameState
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.ui.GameCoreView
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private val dbRule = object : ExternalResource() {
        override fun before() {
            context = ApplicationProvider.getApplicationContext()
            context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString(SettingsRepository.KEY_INSTALL_ID, "main-activity-test-player")
                .commit()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .build()
            DatabaseProvider.setDatabase(db)
        }

        override fun after() {
            db.close()
            DatabaseProvider.setDatabase(null)
            context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(dbRule).around(composeRule)

    private fun resumeNodeCount(): Int =
        composeRule.onAllNodesWithText(context.getString(R.string.pause_resume))
            .fetchSemanticsNodes().size

    @Test
    fun `pause button shows overlay and resume hides it`() {
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(0, resumeNodeCount())

        composeRule.onNodeWithText(context.getString(R.string.game_hud_pause)).performClick()
        composeRule.waitUntil(10_000) { resumeNodeCount() > 0 }

        composeRule.onNodeWithText(context.getString(R.string.pause_resume)).performClick()
        composeRule.waitUntil(10_000) { resumeNodeCount() == 0 }
    }

    @Test
    fun `quit button finishes activity from pause overlay`() {
        composeRule.onNodeWithText(context.getString(R.string.game_hud_pause)).performClick()
        composeRule.waitUntil(10_000) { resumeNodeCount() > 0 }

        composeRule.onNodeWithText(context.getString(R.string.pause_quit)).performClick()
        composeRule.waitUntil(10_000) { composeRule.activity.isFinishing }
    }

    @Test
    fun `compose hierarchy hosts game core view`() {
        composeRule.waitForIdle()
        val content = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
        assertNotNull(findGameCoreView(content))
    }

    private fun findGameCoreView(view: View): GameCoreView? = when (view) {
        is GameCoreView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findGameCoreView(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun `mission briefing renders launch chips`() {
        SettingsRepository(context).setDifficulty(com.young.aircraft.data.GameDifficulty.HARD)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.game_hud_chip_sector, 1)).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.game_hud_chip_difficulty, context.getString(R.string.difficulty_hard))
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.game_hud_chip_airframe, 1)).assertExists()
    }

    @Test
    fun `low memory event shows pause overlay`() {
        GameStateManager.emit(GameState.LOW_MEMORY)
        composeRule.waitUntil(10_000) { resumeNodeCount() > 0 }
    }
}
