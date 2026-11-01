package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.young.aircraft.R
import com.young.aircraft.common.RepoUpdateConfig
import com.young.aircraft.common.RepoUpdateConfigStore
import com.young.aircraft.data.SettingsRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the startup-gate and policy pages.
 *
 * Both gate activities forward to the next step when their persisted flag is
 * already set — the state of any device that has run the app before — so the
 * setup clears those flags to make the pages render. Read-only otherwise.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GateAndPolicyScreensTest {

    @Before
    fun resetStartupGates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepository(context).apply {
            setPrivacyPolicyAccepted(false)
            setOnboardingCompleted(false)
        }
    }

    @Test
    fun privacyPolicyAcceptScreenRenders() {
        withScreen(PrivacyPolicyAcceptActivity::class.java) { screen ->
            // Fresh consent (no earlier version stored) shows the briefing header.
            assertTrue("consent header", screen.waitForText(R.string.mission_briefing_title))
        }
    }

    @Test
    fun onboardingScreenRendersFirstPage() {
        withScreen(OnboardingActivity::class.java) { screen ->
            // Page 0 of the carousel is CONTROLS; the SKIP action lives in the
            // header and is therefore present on every page.
            assertTrue("onboarding first page", screen.waitForText(R.string.onboarding_controls_title))
            assertTrue("onboarding skip action", screen.waitForText(R.string.onboarding_skip))
        }
    }

    @Test
    fun onboardingNextAdvancesToSecondPage() {
        withScreen(OnboardingActivity::class.java) { screen ->
            assertTrue("first page", screen.waitForText(R.string.onboarding_controls_title))
            assertTrue("next action must be clickable", screen.clickTag("btn_next"))
            assertTrue("second page", screen.waitForText(R.string.onboarding_powerups_title))
        }
    }

    @Test
    fun onboardingSkipOpensLaunchHub() {
        withScreen(OnboardingActivity::class.java) { screen ->
            assertTrue("skip action", screen.waitForText(R.string.onboarding_skip))
            assertTrue("skip action must be clickable", screen.clickTag("btn_skip"))

            val hub = awaitResumed(LaunchActivity::class.java, timeoutMs = 60_000)
            assertNotNull("LaunchActivity must resume after SKIP", hub)
            hub?.closeScreen()
        }
    }

    @Test
    fun privacyPolicyScreenRenders() {
        withScreen(PrivacyPolicyActivity::class.java) { screen ->
            assertTrue("policy banner", screen.waitForText(R.string.privacy_policy_banner_title))
        }
    }

    @Test
    fun mandatoryUpdateScreenRenders() {
        // The activity re-checks the enforced minimum on its own resume and
        // finishes immediately when the installed build is current, so the test
        // forces a required version for the duration of this case.
        withForcedUpdate("99.0.0") {
            withScreen(MandatoryUpdateActivity::class.java) { screen ->
                assertTrue("update title", screen.waitForText(R.string.remote_update_required_title))
                assertTrue("current version row", screen.waitForText(R.string.remote_update_current_version))
            }
        }
    }

    @Test
    fun dataPrivacyScreenRenders() {
        withScreen(DataPrivacyActivity::class.java) { screen ->
            assertTrue("data privacy title", screen.waitForText(R.string.privacy_data_screen_title))
        }
    }

    /**
     * Runs [block] while [RepoUpdateConfigStore] reports a forced minimum version,
     * then restores both the cached config and the hook the application installed.
     * The hook re-applies the forced value after an async refresh, which would
     * otherwise restore the real (already satisfied) minimum mid-test.
     */
    private fun withForcedUpdate(minimumVersion: String, block: () -> Unit) {
        val field = RepoUpdateConfigStore::class.java.getDeclaredField("cached").apply { isAccessible = true }
        val previousConfig = field.get(RepoUpdateConfigStore)
        val previousHook = RepoUpdateConfigStore.onUpdated
        val forced = RepoUpdateConfig(minimumVersion = minimumVersion, latestVersion = minimumVersion)
        // Install the hook first: a refresh that lands between the two statements
        // would otherwise overwrite the forced value with the real config.
        RepoUpdateConfigStore.onUpdated = {
            field.set(RepoUpdateConfigStore, forced)
            previousHook?.invoke()
        }
        field.set(RepoUpdateConfigStore, forced)
        try {
            block()
        } finally {
            RepoUpdateConfigStore.onUpdated = previousHook
            field.set(RepoUpdateConfigStore, previousConfig)
        }
    }
}
