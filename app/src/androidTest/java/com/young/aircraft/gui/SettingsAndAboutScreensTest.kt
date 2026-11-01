package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.young.aircraft.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the navigation pages opened from the launch hub.
 * Read-only: no settings rows are toggled.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsAndAboutScreensTest {

    @Test
    fun settingsScreenRenders() {
        withScreen(SettingsActivity::class.java) { screen ->
            assertTrue("settings title", screen.waitForText(R.string.title_activity_settings))
            assertTrue("game settings entry", screen.waitForText(R.string.game_settings_title))
        }
    }

    @Test
    fun settingsRowOpensGameSettings() {
        withScreen(SettingsActivity::class.java) { screen ->
            assertTrue("game settings entry", screen.waitForText(R.string.game_settings_title))
            assertTrue("entry must be clickable", screen.clickText(R.string.game_settings_title))

            val next = awaitResumed(GameSettingsActivity::class.java, timeoutMs = 60_000)
            assertNotNull("GameSettingsActivity must resume after the row tap", next)
            next?.closeScreen()
        }
    }

    @Test
    fun gameSettingsScreenRenders() {
        withScreen(GameSettingsActivity::class.java) { screen ->
            assertTrue("game settings title", screen.waitForText(R.string.game_settings_title))
            assertTrue("sound entry", screen.waitForText(R.string.background_sound_title))
        }
    }

    @Test
    fun languageSettingsScreenRenders() {
        withScreen(LanguageSettingsActivity::class.java) { screen ->
            assertTrue("language title", screen.waitForText(R.string.language_settings_title))
            assertTrue("english entry", screen.waitForText(R.string.language_english))
        }
    }

    @Test
    fun historyScreenRenders() {
        withScreen(HistoryActivity::class.java) { screen ->
            // The header reads LEADERBOARD; the summary card renders in both the
            // empty and the populated state.
            assertTrue("history header", screen.waitForText(R.string.leaderboard_title))
            assertTrue("history summary", screen.waitForText(R.string.history_summary_title))
        }
    }

    @Test
    fun aboutAircraftScreenRenders() {
        withScreen(AboutAircraftActivity::class.java) { screen ->
            assertTrue("about title", screen.waitForText(R.string.about_aircraft_title))
            assertTrue("update entry", screen.waitForText(R.string.about_check_update_title))
        }
    }

    @Test
    fun aboutMeScreenRenders() {
        withScreen(AboutMeActivity::class.java) { screen ->
            assertTrue("about me title", screen.waitForText(R.string.about_me_title))
        }
    }
}
