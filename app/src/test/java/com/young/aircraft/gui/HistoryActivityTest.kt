package com.young.aircraft.gui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.AppDatabase
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryActivityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

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
    }

    private fun drainAsyncWork() {
        repeat(3) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun `empty state is shown when no history exists`() = runTest {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            drainAsyncWork()

            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.recycler_history).visibility)
                assertTrue(
                    activity.findViewById<TextView>(R.id.tv_record_count_chip)
                        .text
                        .toString()
                        .contains("0")
                )
                assertEquals(
                    activity.getString(R.string.history_summary_best_score_empty),
                    activity.findViewById<TextView>(R.id.tv_best_score_chip).text.toString()
                )
            }
        }
    }

    @Test
    fun `summary shows top pilot and list when records exist`() = runTest {
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = "ace-pilot",
                playerName = "Ace Pilot",
                level = 10,
                score = 12_345
            )
        )
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = "wingman",
                playerName = "Wingman",
                level = 8,
                score = 9_999
            )
        )

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            drainAsyncWork()

            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.empty_state).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recycler_history).visibility)
                assertTrue(
                    activity.findViewById<TextView>(R.id.tv_record_count_chip)
                        .text
                        .toString()
                        .contains("2")
                )
                assertTrue(
                    activity.findViewById<TextView>(R.id.tv_best_score_chip)
                        .text
                        .toString()
                        .contains("12,345")
                )
                assertTrue(
                    activity.findViewById<TextView>(R.id.tv_summary_overview)
                        .text
                        .toString()
                        .contains("Ace Pilot")
                )
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            drainAsyncWork()

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_back).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
