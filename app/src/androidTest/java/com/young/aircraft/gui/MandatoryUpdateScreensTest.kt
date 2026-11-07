package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.RepoUpdateConfig
import com.young.aircraft.common.RepoUpdateConfigStore
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the forced-update gate.
 *
 * Directly launching [MandatoryUpdateActivity] makes AircraftApplication
 * finish it again on resume when the current version is no longer below the
 * minimum. Seeding the cached repo fallback above the installed version
 * keeps the gate reachable for render assertions without touching Firebase.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MandatoryUpdateScreensTest {

    @Before
    fun resetRepoFallback() {
        // Keep the activity alive: minimum above the installed version makes
        // it "below minimum", so the lifecycle gate deliberately shows it.
        setCached(RepoUpdateConfig(minimumVersion = "99.0.0", latestVersion = "99.0.0"))
    }

    @Test
    fun mandatoryUpdateScreenRendersUpdateRequired() {
        withScreen(MandatoryUpdateActivity::class.java) { screen ->
            assertTrue("status pill", screen.waitForText(R.string.remote_update_required_status))
            assertTrue("title", screen.waitForText(R.string.remote_update_required_title))
            assertTrue("update action", screen.waitForText(R.string.remote_update_required_action))
            assertTrue("current version row", screen.waitForText(R.string.remote_update_current_version))
            assertTrue("minimum version row", screen.waitForText(R.string.remote_update_minimum_version))
            assertTrue("current version shown", screen.waitForText(BuildConfig.VERSION_NAME))
        }
    }

    // cached has a private setter and androidTest is a separate APK, so
    // reflection is the only way to seed the store without changing production.
    private fun setCached(config: RepoUpdateConfig) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val field = RepoUpdateConfigStore::class.java.getDeclaredField("cached")
            field.isAccessible = true
            field.set(RepoUpdateConfigStore, config)
        }
    }
}
