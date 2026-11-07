package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.RepoUpdateConfig
import com.young.aircraft.common.RepoUpdateConfigStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the forced-update gate.
 *
 * Directly launching [MandatoryUpdateActivity] makes AircraftApplication
 * finish it again on resume when the current version is no longer below the
 * minimum. Pinning the store above the installed version keeps the gate
 * reachable for render assertions without touching Firebase: the startup
 * fetch of app-update.json and Remote Config both settle asynchronously, and
 * on a networked device they would replace any seeded value within seconds.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MandatoryUpdateScreensTest {

    @Before
    fun pinMinimumAboveInstalledVersion() {
        RepoUpdateConfigStore.testOverride =
            RepoUpdateConfig(minimumVersion = "99.0.0", latestVersion = "99.0.0")
    }

    @After
    fun clearPin() {
        // The whole suite shares one app process; leave the real gate intact.
        RepoUpdateConfigStore.testOverride = null
    }

    @Test
    fun mandatoryUpdateScreenRendersUpdateRequired() {
        withScreen(MandatoryUpdateActivity::class.java) { screen ->
            assertTrue("status pill", screen.waitForText(R.string.remote_update_required_status))
            assertTrue("title", screen.waitForText(R.string.remote_update_required_title))
            assertTrue("update action", screen.waitForText(R.string.remote_update_required_action))
            assertTrue("current version row", screen.waitForText(R.string.remote_update_current_version))
            assertTrue("minimum version row", screen.waitForText(R.string.remote_update_minimum_version))
            // Proves the screen read the pinned value, not app-update.json.
            assertTrue("pinned minimum shown", screen.waitForText("99.0.0"))
            assertTrue("current version shown", screen.waitForText(BuildConfig.VERSION_NAME))
        }
    }
}
