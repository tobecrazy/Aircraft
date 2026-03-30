package com.young.aircraft.gui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchActivityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)

        val settingsRepository = SettingsRepository(context)
        settingsRepository.setPrivacyPolicyAccepted(true)
        settingsRepository.setOnboardingCompleted(true)
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsRepository.KEY_INSTALL_ID, "test-player-id")
            .commit()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db)
    }

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.setDatabase(null)
        Dispatchers.resetMain()

        // Clear prefs
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `launching with no saved data starts new game with default jet`() = runTest {
        val activity = Robolectric.buildActivity(LaunchActivity::class.java).setup().get()

        activity.binding.startGame.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(0, nextIntent.getIntExtra("jet_plane_index", -1))
        assertEquals(Aircraft.JET_PLANES[0], nextIntent.getIntExtra("jet_plane_res", -1))
    }

    @Test
    fun `launching with saved level 1 starts new game directly`() = runTest {
        db.playerGameDataDao()
            .insert(PlayerGameData(playerId = "test-player-id", level = 1, score = 0, jetPlaneIndex = 1))

        val activity = Robolectric.buildActivity(LaunchActivity::class.java).setup().get()
        activity.binding.startGame.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        // New game uses currently selected jet (0 by default)
        assertEquals(0, nextIntent.getIntExtra("jet_plane_index", -1))
    }

    @Test
    fun `launching with saved level 2 shows continue dialog and handles continue`() = runTest {
        // Save level 5 with jet index 2
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = "test-player-id",
                level = 5,
                score = 1000,
                jetPlaneIndex = 2
            )
        )

        val activity = Robolectric.buildActivity(LaunchActivity::class.java).setup().get()
        activity.binding.startGame.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // Check that AlertDialog is shown
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Dialog should not be null", dialog)

        // Click continue (positive button in custom layout)
        val positiveBtn =
            dialog.findViewById<android.view.View>(com.young.aircraft.R.id.dialog_positive_btn)
        assertNotNull("Positive button not found", positiveBtn)
        positiveBtn?.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null after continue", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(5, nextIntent.getIntExtra("start_level", -1))
        assertEquals(2, nextIntent.getIntExtra("jet_plane_index", -1))
    }

    @Test
    fun `resolves legacy saved jet resource ID to correct index`() = runTest {
        // Legacy data: index is 0 but jet_plane_res is jet_plane_4 (which is index 2)
        val jetResId = Aircraft.JET_PLANES[2]
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = "test-player-id",
                level = 3,
                score = 500,
                jetPlaneRes = jetResId,
                jetPlaneIndex = -1 // Simulate old data or missing index
            )
        )

        val activity = Robolectric.buildActivity(LaunchActivity::class.java).setup().get()
        activity.binding.startGame.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Dialog should be shown for legacy data", dialog)
        val positiveBtn =
            dialog?.findViewById<android.view.View>(com.young.aircraft.R.id.dialog_positive_btn)
        assertNotNull("Positive button not found in legacy dialog", positiveBtn)
        positiveBtn?.performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Next intent should not be null for legacy resolution", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertEquals(3, nextIntent.getIntExtra("start_level", -1))
        assertEquals(2, nextIntent.getIntExtra("jet_plane_index", -1))
    }
}
