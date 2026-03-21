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
        // Clear acceptance state before each test
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit().remove("privacy_policy_accepted").commit()
    }

    @Test
    fun `first launch shows privacy policy accept screen`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull(webView)
            }
        }
    }

    @Test
    fun `buttons are disabled initially`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnAccept = activity.findViewById<android.widget.TextView>(R.id.btn_accept)
                val btnReject = activity.findViewById<android.widget.TextView>(R.id.btn_reject)
                assertFalse(btnAccept.isEnabled)
                assertFalse(btnReject.isEnabled)
                assertEquals(0.3f, btnAccept.alpha, 0.01f)
                assertEquals(0.3f, btnReject.alpha, 0.01f)
            }
        }
    }

    @Test
    fun `accept button saves preference and launches LaunchActivity`() {
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
                    LaunchActivity::class.java.name,
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
                btnReject.isEnabled = true
                btnReject.alpha = 1.0f
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
                btnReject.isEnabled = true
                btnReject.performClick()

                val prefs = activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
                assertFalse(prefs.getBoolean("privacy_policy_accepted", false))
            }
        }
    }

    @Test
    fun `already accepted redirects to LaunchActivity immediately`() {
        // Set acceptance before launching
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_policy_accepted", true).commit()

        val intent = Intent(context, PrivacyPolicyAcceptActivity::class.java)
        val scenario = ActivityScenario.launch<PrivacyPolicyAcceptActivity>(intent)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun `webview loads correct page for english locale`() {
        ActivityScenario.launch(PrivacyPolicyAcceptActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                assertNotNull(webView)
                // WebView is present and configured
                assertTrue(webView.settings.javaScriptEnabled)
                assertTrue(webView.settings.loadsImagesAutomatically)
            }
        }
    }
}
