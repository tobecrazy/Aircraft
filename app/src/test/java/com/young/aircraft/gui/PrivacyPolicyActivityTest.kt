package com.young.aircraft.gui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
import org.robolectric.shadows.ShadowLooper
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h920dp")
class PrivacyPolicyActivityTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
    }

    private fun root(activity: PrivacyPolicyActivity) =
        activity.window.decorView.findSemanticsOwner()!!.rootSemanticsNode

    /** Two passes: snapshot writes need a choreographer frame, recomposition needs the next. */
    private fun waitForRecomposition() {
        repeat(2) { ShadowLooper.idleMainLooper() }
    }

    /** The AndroidView-hosted WebView, found by walking the view tree. */
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findWebView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun `activity launches with privacy summary and loading state`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }

                assertTrue(activity.getString(R.string.privacy_policy_summary) in texts)
                assertTrue(activity.getString(R.string.privacy_policy_source_chip) in texts)
                // Loading overlay renders its label while the page loads.
                assertTrue(activity.getString(R.string.privacy_policy_loading) in texts)

                val webView = findWebView(activity.window.decorView)
                assertNotNull(webView)
                assertTrue(webView!!.settings.javaScriptEnabled)
                assertTrue(webView.settings.loadsImagesAutomatically)
            }
        }
    }

    @Test
    fun `successful page load hides loading state`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = findWebView(activity.window.decorView)!!
                val shadowWebView = shadowOf(webView)

                shadowWebView.webViewClient.onPageFinished(webView, shadowWebView.lastLoadedUrl)
                waitForRecomposition()

                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }
                assertFalse(activity.getString(R.string.privacy_policy_loading) in texts)
                assertFalse(activity.getString(R.string.privacy_policy_error_title) in texts)

                val languageChip = findAllNodes(root(activity))
                    .mapNotNull { it.displayText() }
                    .firstOrNull {
                        it == activity.getString(R.string.privacy_policy_language_en) ||
                            it == activity.getString(R.string.privacy_policy_language_zh)
                    }
                assertFalse(languageChip.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `external links open outside the embedded webview`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = findWebView(activity.window.decorView)!!
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
                val webView = findWebView(activity.window.decorView)!!
                val request = mock<WebResourceRequest>()
                val error = mock<WebResourceError>()

                whenever(request.isForMainFrame).thenReturn(true)
                whenever(request.url).thenReturn("file:///android_asset/privacy_policy_en.html".toUri())
                whenever(error.description).thenReturn("Load failed")

                shadowOf(webView).webViewClient.onReceivedError(webView, request, error)
                waitForRecomposition()

                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }
                assertFalse(activity.getString(R.string.privacy_policy_loading) in texts)
                assertTrue(activity.getString(R.string.privacy_policy_error_title) in texts)

                assertTrue(root(activity).clickOnTag("btn_retry"))
                waitForRecomposition()

                val afterRetry = findAllNodes(root(activity)).mapNotNull { it.displayText() }
                assertTrue(activity.getString(R.string.privacy_policy_loading) in afterRetry)
                assertFalse(activity.getString(R.string.privacy_policy_error_title) in afterRetry)
                assertFalse(shadowOf(webView).lastLoadedUrl.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("btn_back"))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
