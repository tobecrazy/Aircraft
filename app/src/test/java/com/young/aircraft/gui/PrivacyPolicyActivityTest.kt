package com.young.aircraft.gui

import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import com.young.aircraft.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyActivityTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @Test
    fun `activity launches with privacy summary and loading state`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val summary = activity.findViewById<TextView>(R.id.tv_policy_summary)
                val sourceChip = activity.findViewById<TextView>(R.id.tv_source_chip)
                val webView = activity.findViewById<WebView>(R.id.web_view)

                assertEquals(
                    activity.getString(R.string.privacy_policy_summary),
                    summary.text.toString()
                )
                assertEquals(
                    activity.getString(R.string.privacy_policy_source_chip),
                    sourceChip.text.toString()
                )
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loading_state).visibility)
                assertTrue(webView.settings.javaScriptEnabled)
                assertTrue(webView.settings.loadsImagesAutomatically)
            }
        }
    }

    @Test
    fun `successful page load hides loading state`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                val shadowWebView = shadowOf(webView)

                shadowWebView.webViewClient.onPageFinished(webView, shadowWebView.lastLoadedUrl)

                assertEquals(View.GONE, activity.findViewById<View>(R.id.loading_state).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.error_state).visibility)
                assertTrue(activity.findViewById<TextView>(R.id.tv_language_chip).text.isNotEmpty())
            }
        }
    }

    @Test
    fun `external links open outside the embedded webview`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                val shadowWebView = shadowOf(webView)
                val handled = shadowWebView.webViewClient.shouldOverrideUrlLoading(
                    webView,
                    "https://github.com/tobecrazy"
                )

                assertTrue(handled)
                assertEquals(
                    "file:///android_asset/privacy_policy_en.html",
                    shadowWebView.lastLoadedUrl
                )

                val nextIntent = shadowOf(activity).nextStartedActivity
                assertNotNull(nextIntent)
                assertEquals("android.intent.action.VIEW", nextIntent.action)
                assertEquals("https://github.com/tobecrazy", nextIntent.dataString)
            }
        }
    }

    @Test
    fun `main frame load error reveals retry state`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.web_view)
                val request = mock<WebResourceRequest>()
                val error = mock<WebResourceError>()

                whenever(request.isForMainFrame).thenReturn(true)
                whenever(request.url).thenReturn("file:///android_asset/privacy_policy_en.html".toUri())
                whenever(error.description).thenReturn("Load failed")

                shadowOf(webView).webViewClient.onReceivedError(webView, request, error)

                assertEquals(View.GONE, activity.findViewById<View>(R.id.loading_state).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.error_state).visibility)

                activity.findViewById<View>(R.id.btn_retry).performClick()

                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loading_state).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.error_state).visibility)
                assertFalse(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_back).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
