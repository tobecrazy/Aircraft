package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.young.aircraft.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected verification of the launch hub. The activity is really rendered on
 * a device or emulator while the test runs.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LaunchActivityScreenTest {

    @Test
    fun launchHubScreenShowsTitleHintAndActions() {
        withScreen(LaunchActivity::class.java) { hub ->
            // The entrance animation runs on the real clock; polling tolerates it.
            assertTrue(
                "hub title must render",
                hub.waitForText(scenarioString(R.string.app_name).uppercase())
            )
            assertTrue("jet hint must render", hub.waitForText(R.string.launch_jet_hint))
            assertTrue("start action must be present", hub.waitForText(R.string.launch_start_mission))
        }
    }

    @Test
    fun startMissionNavigatesToCombatScreen() {
        withScreen(LaunchActivity::class.java) { hub ->
            assertTrue("start action must render", hub.waitForText(R.string.launch_start_mission))

            // A stored save opens the resume dialog first; both paths lead into
            // MainActivity, so pick whichever action the hub is offering.
            val clicked = if (hub.hasTagNow("saved_game_dialog")) {
                hub.clickTag("dialog_btn_continue") || hub.clickTag("dialog_btn_new_game")
            } else {
                hub.clickTag("btn_start_mission")
            }
            assertTrue("launch action node must exist", clicked)

            val game = awaitResumed(MainActivity::class.java, timeoutMs = 60_000)
            assertNotNull("MainActivity must resume after START MISSION", game)
            game?.closeScreen()
        }
    }
}
