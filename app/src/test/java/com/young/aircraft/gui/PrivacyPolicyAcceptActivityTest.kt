package com.young.aircraft.gui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyPolicyAcceptActivityTest {

    @get:Rule(order = 0)
    val clearPrefsRule = object : ExternalResource() {
        override fun before() {
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED)
                .remove("onboarding_completed")
                .commit()
        }
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<PrivacyPolicyAcceptActivity>()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        // Infinite neon pulse on the accept button never quiesces — freeze the clock.
        composeTestRule.mainClock.autoAdvance = false
    }

    /** Advances the frozen compose clock so pending recompositions land. */
    private fun tick(ms: Long = 32) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        composeTestRule.waitForIdle()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun expectedAssetUrl(): String {
        val page =
            if (Locale.getDefault().language == AircraftConstants.PrivacyPolicy.LANG_ZH) {
                AircraftConstants.PrivacyPolicy.ASSET_ZH
            } else {
                AircraftConstants.PrivacyPolicy.ASSET_EN
            }
        return "${AircraftConstants.PrivacyPolicy.ASSET_PREFIX}$page"
    }

    @Test
    fun `already accepted routes to OnboardingActivity immediately`() {
        prefs.edit().putBoolean(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED, true).commit()

        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<PrivacyPolicyAcceptActivity>(
            Intent(context, PrivacyPolicyAcceptActivity::class.java)
        ).use { scenario ->
            // Gated before setContent → the launched instance destroys itself.
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
        val routed = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        assertEquals(OnboardingActivity::class.java.name, routed.component?.className)
    }

    @Test
    fun `first launch shows star field, policy web view and both actions`() {
        tick()

        composeTestRule.onNodeWithTag("star_field").assertExists()
        composeTestRule.onNodeWithTag("policy_web_view").assertExists()
        composeTestRule.onNodeWithTag("btn_accept").assertExists()
        composeTestRule.onNodeWithTag("btn_reject").assertExists()

        val webView = findWebView(composeTestRule.activity.window.decorView)
        assertNotNull("WebView must be inflated inside the policy card", webView)
        assertTrue(webView!!.settings.javaScriptEnabled)
        assertTrue(webView.settings.loadsImagesAutomatically)
        assertEquals(expectedAssetUrl(), webView.url)
    }

    @Test
    fun `accept stays inert while the document has not been read`() {
        tick()

        // Disabled Surface still exposes OnClick semantics but must be inert.
        composeTestRule.onNodeWithTag("btn_accept").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("btn_accept").performClick()
        tick()

        assertFalse(prefs.getBoolean(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED, false))
        assertFalse(composeTestRule.activity.isFinishing)
    }

    @Test
    fun `reject finishes without saving acceptance`() {
        tick()

        composeTestRule.onNodeWithTag("btn_reject").performClick()
        tick()

        assertTrue(composeTestRule.activity.isFinishing)
        assertFalse(prefs.getBoolean(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED, false))
    }

    @Test
    fun `reaching document end unlocks accept which saves pref and routes to onboarding`() {
        tick()

        // Robolectric never fires WebViewClient callbacks — drive the unlock path
        // directly: page finish schedules the 500ms can-scroll probe.
        val webView = findWebView(composeTestRule.activity.window.decorView)
        assertNotNull(webView)
        webView!!.webViewClient.onPageFinished(webView, webView.url)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        tick(600)

        composeTestRule.onNodeWithTag("btn_accept").performClick()
        tick()

        assertTrue(prefs.getBoolean(SettingsRepository.KEY_PRIVACY_POLICY_ACCEPTED, false))
        val nextIntent = shadowOf(composeTestRule.activity).nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(OnboardingActivity::class.java.name, nextIntent.component?.className)
        assertTrue(composeTestRule.activity.isFinishing)
    }
}
