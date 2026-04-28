package com.young.aircraft.gui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaunchActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<LaunchActivity>()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val settingsRepository = SettingsRepository(context)
        settingsRepository.setPrivacyPolicyAccepted(true)
        settingsRepository.setOnboardingCompleted(true)
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsRepository.KEY_INSTALL_ID, "test-player-id")
            .commit()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        DatabaseProvider.setDatabase(db)
    }

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.setDatabase(null)

        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun waitForAnimations() {
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `launching with no saved data starts new game with default jet`() {
        waitForAnimations()

        composeTestRule.onNodeWithTag("btn_start_mission").performClick()
        composeTestRule.waitForIdle()

        val activity = composeTestRule.activity
        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(0, nextIntent.getIntExtra("jet_plane_index", -1))
        assertEquals(Aircraft.JET_PLANES[0], nextIntent.getIntExtra("jet_plane_res", -1))
    }

    @Test
    fun `launching with saved level 1 starts new game directly`() {
        runBlocking {
            db.playerGameDataDao()
                .insert(PlayerGameData(playerId = "test-player-id", level = 1, score = 0, jetPlaneIndex = 1))
        }

        waitForAnimations()

        composeTestRule.onNodeWithTag("btn_start_mission").performClick()
        composeTestRule.waitForIdle()

        val activity = composeTestRule.activity
        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(0, nextIntent.getIntExtra("jet_plane_index", -1))
    }

    @Test
    fun `launching with saved level 2 shows continue dialog and handles continue`() {
        runBlocking {
            db.playerGameDataDao().insert(
                PlayerGameData(
                    playerId = "test-player-id",
                    level = 5,
                    score = 1000,
                    jetPlaneIndex = 2
                )
            )
        }

        waitForAnimations()

        composeTestRule.onNodeWithTag("btn_start_mission").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("saved_game_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_btn_continue").performClick()
        composeTestRule.waitForIdle()

        val activity = composeTestRule.activity
        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null after continue", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(5, nextIntent.getIntExtra("start_level", -1))
        assertEquals(2, nextIntent.getIntExtra("jet_plane_index", -1))
    }

    @Test
    fun `resolves legacy saved jet resource ID to correct index`() {
        val jetResId = Aircraft.JET_PLANES[2]
        runBlocking {
            db.playerGameDataDao().insert(
                PlayerGameData(
                    playerId = "test-player-id",
                    level = 3,
                    score = 500,
                    jetPlaneRes = jetResId,
                    jetPlaneIndex = -1
                )
            )
        }

        waitForAnimations()

        composeTestRule.onNodeWithTag("btn_start_mission").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("saved_game_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_btn_continue").performClick()
        composeTestRule.waitForIdle()

        val activity = composeTestRule.activity
        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null for legacy resolution", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(3, nextIntent.getIntExtra("start_level", -1))
        assertEquals(2, nextIntent.getIntExtra("jet_plane_index", -1))
    }
}
