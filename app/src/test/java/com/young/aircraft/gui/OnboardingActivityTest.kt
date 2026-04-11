package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.*
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
class OnboardingActivityTest {

    private lateinit var context: Context

    @get:Rule
    val composeRule = createAndroidComposeRule<OnboardingActivity>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("onboarding_completed")
            .remove("privacy_policy_accepted")
            .commit()
    }

    @Test
    fun `first launch shows onboarding carousel`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_pager").assertExists()
    }

    @Test
    fun `star field is present`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("star_field").assertExists()
    }

    @Test
    fun `already completed redirects to LaunchActivity immediately`() {
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_completed", true).commit()

        val intent = Intent(context, OnboardingActivity::class.java)
        val scenario = ActivityScenario.launch<OnboardingActivity>(intent)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun `skip button saves preference and launches LaunchActivity`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_skip").performClick()
        composeRule.waitForIdle()

        val prefs = composeRule.activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("onboarding_completed", false))

        val shadowActivity = shadowOf(composeRule.activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(LaunchActivity::class.java.name, nextIntent.component?.className)
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `next button navigates to second page`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_next").performClick()
        composeRule.waitForIdle()

        // After navigating to page 2, the power-ups title should be visible
        val powerupsTitle = composeRule.activity.getString(R.string.onboarding_powerups_title)
        composeRule.onNodeWithText(powerupsTitle).assertExists()
    }

    @Test
    fun `launch button on second page saves pref and launches LaunchActivity`() {
        composeRule.waitForIdle()

        // Navigate to page 2
        composeRule.onNodeWithTag("btn_next").performClick()
        composeRule.waitForIdle()

        // Click LAUNCH on page 2
        composeRule.onNodeWithTag("btn_next").performClick()
        composeRule.waitForIdle()

        val prefs = composeRule.activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("onboarding_completed", false))

        val shadowActivity = shadowOf(composeRule.activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(LaunchActivity::class.java.name, nextIntent.component?.className)
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `page indicators exist`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("indicator_0").assertExists()
        composeRule.onNodeWithTag("indicator_1").assertExists()
    }

    @Test
    fun `skip does not navigate to second page first`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_skip").performClick()
        composeRule.waitForIdle()

        // Should go directly to LaunchActivity, not page 2
        val shadowActivity = shadowOf(composeRule.activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertEquals(LaunchActivity::class.java.name, nextIntent.component?.className)
    }
}
