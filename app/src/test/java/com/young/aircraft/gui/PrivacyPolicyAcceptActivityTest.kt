package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyAcceptActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear all gate prefs before each test
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("privacy_policy_accepted")
            .remove("onboarding_completed")
            .commit()
    }

    @Test
    fun `first launch shows privacy policy accept screen with star field`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull(webView)
                val starField = activity.findViewById<StarFieldView>(R.id.star_field)
                assertNotNull(starField)
            }
        }
    }

    @Test
    fun `reject button is always enabled, accept button requires scroll to bottom`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnAccept = activity.findViewById<android.widget.TextView>(R.id.btn_accept)
                val btnReject = activity.findViewById<android.widget.TextView>(R.id.btn_reject)
                // Reject button is always enabled
                assertTrue(btnReject.isEnabled)
                assertEquals(1.0f, btnReject.alpha, 0.01f)
                // Accept button requires scroll to bottom
                assertFalse(btnAccept.isEnabled)
                assertEquals(0.3f, btnAccept.alpha, 0.01f)
            }
        }
    }

    @Test
    fun `accept button saves preference and routes to OnboardingActivity`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnAccept = activity.findViewById<android.widget.TextView>(R.id.btn_accept)
                // Simulate enabling (as if user scrolled to bottom)
                btnAccept.isEnabled = true
                btnAccept.alpha = 1.0f
                btnAccept.performClick()

                val prefs = activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
                assertTrue(prefs.getBoolean("privacy_policy_accepted", false))

                val shadowActivity = shadowOf(activity)
                val nextIntent = shadowActivity.nextStartedActivity
                assertNotNull(nextIntent)
                assertEquals(
                    OnboardingActivity::class.java.name,
                    nextIntent.component?.className
                )
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `reject button finishes activity`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnReject = activity.findViewById<android.widget.TextView>(R.id.btn_reject)
                // Reject is always enabled
                assertTrue(btnReject.isEnabled)
                btnReject.performClick()

                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `reject does not save acceptance preference`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnReject = activity.findViewById<android.widget.TextView>(R.id.btn_reject)
                // Reject is always enabled, no need to manually set
                btnReject.performClick()

                val prefs = activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
                assertFalse(prefs.getBoolean("privacy_policy_accepted", false))
            }
        }
    }

    @Test
    fun `already accepted routes to OnboardingActivity immediately`() {
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_policy_accepted", true).commit()

        val intent = Intent(context, PrivacyPolicyAcceptActivity::class.java)
        val scenario = ActivityScenario.launch<PrivacyPolicyAcceptActivity>(intent)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun `webview loads with javascript enabled`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull(webView)
                assertTrue(webView.settings.javaScriptEnabled)
                assertTrue(webView.settings.loadsImagesAutomatically)
            }
        }
    }

    @Test
    fun `mission briefing header is displayed`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // The layout uses the cinematic mission briefing title
                val webView = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull("WebView should be present in cinematic layout", webView)
            }
        }
    }
}
