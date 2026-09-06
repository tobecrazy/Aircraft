package com.young.aircraft.gui

import android.content.Context
import android.os.Looper
import android.widget.Button
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// w420dp-h920dp: Robolectric's default 320x470 window is too short for the summary
// hero card + one record card to fit, which starves any viewport-based assertions.
@Config(sdk = [34], qualifiers = "w420dp-h920dp")
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

    /** Runs pending coroutines, looper tasks and recompositions until the UI settles. */
    private fun drainAsyncWork() {
        repeat(3) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
        }
    }

    private fun root(activity: com.young.aircraft.gui.HistoryActivity): SemanticsNode =
        activity.window.decorView.findSemanticsOwner()!!.rootSemanticsNode

    private val twoRecords = listOf(
        PlayerGameData(playerId = "ace-pilot", playerName = "Ace Pilot", level = 10, score = 12_345),
        PlayerGameData(playerId = "wingman", playerName = "Wingman", level = 9, score = 9_999, difficulty = "1.2")
    )

    @Test
    fun `empty database shows empty state instead of grid`() {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                drainAsyncWork()
                val nodes = findAllNodes(root(activity))

                assertTrue(nodes.any { it.displayText() == context.getString(R.string.history_no_records) })
                assertTrue(nodes.any { it.displayText() == context.getString(R.string.history_empty_summary) })
                assertTrue(
                    nodes.any {
                        it.displayText() == context.getString(R.string.history_summary_record_count, 0)
                    }
                )
                assertTrue(
                    nodes.any {
                        it.displayText() == context.getString(R.string.history_summary_best_score_empty)
                    }
                )
            }
        }
    }

    @Test
    fun `records render with summary chips and top record badge`() {
        seed(*twoRecords.toTypedArray())

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                drainAsyncWork()
                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }

                assertTrue("Ace Pilot" in texts)
                assertTrue(context.getString(R.string.level, "10") in texts)
                assertTrue("12,345" in texts)
                // Only the top record carries the gold TOP RECORD badge label.
                assertTrue(context.getString(R.string.history_top_record_label) in texts)
                assertTrue(context.getString(R.string.difficulty_easy) in texts)
                assertTrue(context.getString(R.string.difficulty_normal) in texts)
                assertTrue(
                    context.getString(R.string.history_summary_with_top_pilot, "Ace Pilot", 10) in texts
                )
                assertTrue(
                    context.getString(R.string.history_summary_best_score, "12,345") in texts
                )
            }
        }
    }

    @Test
    fun `delete confirmation removes the record`() {
        seed(*twoRecords.toTypedArray())

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                drainAsyncWork()

                // First card's delete button (content description = history_delete).
                val deleteNode = findAllNodes(root(activity)).firstOrNull {
                    it.displayText() == context.getString(R.string.history_delete)
                }
                assertNotNull(deleteNode)
                deleteNode!!.config[SemanticsActions.OnClick].action?.invoke()
                shadowOf(Looper.getMainLooper()).idle()

                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull("Delete confirmation should be shown", dialog)
                assertTrue(dialog.isShowing)
                dialog.findViewById<Button>(android.R.id.button1).performClick()
                drainAsyncWork()

                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }
                assertFalse("Ace Pilot" in texts)
                assertTrue("Wingman" in texts)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                drainAsyncWork()
                assertTrue(root(activity).clickOnTag("btn_back"))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(activity.isFinishing)
            }
        }
    }

    private fun seed(vararg records: PlayerGameData) {
        kotlinx.coroutines.runBlocking {
            records.forEach { db.playerGameDataDao().insert(it) }
        }
    }
}
