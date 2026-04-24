package com.young.aircraft.gui

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameState
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

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

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.setDatabase(null)
        Dispatchers.resetMain()
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun drainAsyncWork() {
        repeat(3) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun `pause button shows overlay and resume hides it`() = runTest {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        drainAsyncWork()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.pause_overlay).visibility)

        activity.findViewById<View>(R.id.btn_pause).performClick()
        drainAsyncWork()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pause_overlay).visibility)

        activity.findViewById<View>(R.id.btn_resume).performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        drainAsyncWork()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.pause_overlay).visibility)
    }

    @Test
    fun `quit button finishes activity from pause overlay`() = runTest {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        drainAsyncWork()

        activity.findViewById<View>(R.id.btn_pause).performClick()
        drainAsyncWork()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pause_overlay).visibility)

        activity.findViewById<View>(R.id.btn_quit).performClick()
        drainAsyncWork()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `game container hosts game core view`() = runTest {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        drainAsyncWork()

        val gameContainer = activity.findViewById<android.widget.FrameLayout>(R.id.game_container)
        assertEquals(1, gameContainer.childCount)
        assertTrue(gameContainer.getChildAt(0) is com.young.aircraft.ui.GameCoreView)
    }

    @Test
    fun `mission briefing reflects launch sector difficulty and airframe`() = runTest {
        SettingsRepository(context).setDifficulty(GameDifficulty.HARD)

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            putExtra("start_level", 4)
            putExtra("jet_plane_index", 2)
            putExtra("jet_plane_res", com.young.aircraft.ui.Aircraft.JET_PLANES[2])
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).create().get()
        drainAsyncWork()

        assertEquals(
            activity.getString(R.string.game_hud_chip_sector, 4),
            activity.findViewById<android.widget.TextView>(R.id.tv_sector_chip).text.toString()
        )
        assertEquals(
            activity.getString(R.string.game_hud_chip_difficulty, activity.getString(R.string.difficulty_hard)),
            activity.findViewById<android.widget.TextView>(R.id.tv_difficulty_chip).text.toString()
        )
        assertEquals(
            activity.getString(R.string.game_hud_chip_airframe, 3),
            activity.findViewById<android.widget.TextView>(R.id.tv_airframe_chip).text.toString()
        )
    }

    @Test
    fun `low memory event shows pause overlay`() = runTest {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        drainAsyncWork()

        GameStateManager.emit(GameState.LOW_MEMORY)
        drainAsyncWork()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pause_overlay).visibility)
        assertTrue(activity.findViewById<View>(R.id.pause_panel).alpha >= 0f)
    }
}
