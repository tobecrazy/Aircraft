package com.young.aircraft.gui

import android.content.Context
import android.graphics.Typeface
import android.os.Looper
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.ImageDetailsIntentContract
import com.young.richtext.RichTextEditorView
import com.young.richtext.R as RichTextR
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RichTextEditorActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    // Screen content is Compose — traverse the window's semantics tree.
    private fun screenRoot(activity: RichTextEditorActivity) =
        requireNotNull(activity.window.decorView.findSemanticsOwner()).rootSemanticsNode

    private fun findEditor(activity: RichTextEditorActivity): EditText =
        requireNotNull(activity.richEditorView).editor

    // ── Activity lifecycle ───────────────────────────────────

    @Test
    fun `activity launches in edit mode with editable rich text input`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                assertTrue(editor.isEnabled)
                assertTrue(editor.isFocusable)
                assertNotNull(editor.keyListener)
                // Edit mode: no preview WebView is attached yet.
                assertNull(activity.previewWebView)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(screenRoot(activity).clickOnTag("btn_back"))
                assertTrue(activity.isFinishing)
            }
        }
    }

    // ── Mode toggle ──────────────────────────────────────────

    @Test
    fun `clicking preview swaps in a WebView and back restores the editor`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(screenRoot(activity).clickOnTag("btn_preview_mode"))
                idle()
                val webView = requireNotNull(activity.previewWebView)

                assertTrue(screenRoot(activity).clickOnTag("btn_edit_mode"))
                idle()
                // Leaving preview disposes the WebView (destroyed via onDispose callback).
                assertNull(activity.previewWebView)

                val editor = findEditor(activity)
                assertTrue(editor.isEnabled)
                assertNotNull(editor.keyListener)
            }
        }
    }

    @Test
    fun `clicking json loads editable example content`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(screenRoot(activity).clickOnTag("btn_load_example_json"))
                idle()

                val editor = findEditor(activity)
                val content = editor.text.toString()
                assertTrue(content.contains("a good day"))
                assertTrue(content.contains("https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png"))
                assertFalse(content.contains("data:image/png;base64"))
                assertEquals(
                    context.getString(R.string.rich_text_example_json_loaded),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `makeHtmlEditable omits embedded data image tags`() {
        val html = "<p><img src=\"data:image/png;base64,abc123\" /></p><p>keep</p>"

        val editable = RichTextEditorActivity.makeHtmlEditable(html)

        assertEquals("<p><span>[embedded image omitted]</span></p><p>keep</p>", editable)
    }

    // ── Formatting without selection shows snackbar ─────────────

    @Test
    fun `bold without selection shows snackbar`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(RichTextR.id.rich_btn_bold).performClick()
                assertEquals(
                    context.getString(RichTextR.string.rich_text_select_text),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `italic without selection shows snackbar`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(RichTextR.id.rich_btn_italic).performClick()
                assertEquals(
                    context.getString(RichTextR.string.rich_text_select_text),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `underline without selection shows snackbar`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(RichTextR.id.rich_btn_underline).performClick()
                assertEquals(
                    context.getString(RichTextR.string.rich_text_select_text),
                    snackbarText(activity)
                )
            }
        }
    }

    // ── Span application ─────────────────────────────────────

    @Test
    fun `bold applies StyleSpan BOLD to selected text`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello world")
                editor.setSelection(0, 5)

                activity.findViewById<View>(RichTextR.id.rich_btn_bold).performClick()

                val spannable = editor.text as Spanned
                val spans = spannable.getSpans(0, 5, StyleSpan::class.java)
                assertTrue(spans.isNotEmpty())
                assertEquals(Typeface.BOLD, spans[0].style)
            }
        }
    }

    @Test
    fun `italic applies StyleSpan ITALIC to selected text`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello world")
                editor.setSelection(6, 11)

                activity.findViewById<View>(RichTextR.id.rich_btn_italic).performClick()

                val spannable = editor.text as Spanned
                val spans = spannable.getSpans(6, 11, StyleSpan::class.java)
                assertTrue(spans.isNotEmpty())
                assertEquals(Typeface.ITALIC, spans[0].style)
            }
        }
    }

    @Test
    fun `underline applies UnderlineSpan to selected text`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello world")
                editor.setSelection(0, 5)

                activity.findViewById<View>(RichTextR.id.rich_btn_underline).performClick()

                val spannable = editor.text as Spanned
                val spans = spannable.getSpans(0, 5, UnderlineSpan::class.java)
                assertTrue(spans.isNotEmpty())
            }
        }
    }

    // ── Markdown toggle ──────────────────────────────────────

    @Test
    fun `markdown toggle shows enabled snackbar`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(RichTextR.id.rich_btn_markdown).performClick()
                assertEquals(
                    context.getString(RichTextR.string.rich_text_md_on),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `markdown double toggle shows disabled snackbar`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btn = activity.findViewById<View>(RichTextR.id.rich_btn_markdown)
                btn.performClick() // ON
                btn.performClick() // OFF
                assertEquals(
                    context.getString(RichTextR.string.rich_text_md_off),
                    snackbarText(activity)
                )
            }
        }
    }

    // ── Navigation from DevelopSettings ──────────────────────

    @Test
    fun `rich text button navigates to RichTextEditorActivity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    requireNotNull(activity.window.decorView.findSemanticsOwner()).rootSemanticsNode
                        .clickOnTag("btn_test_rich_text")
                )
                val intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(
                    RichTextEditorActivity::class.java.name,
                    requireNotNull(intent).component?.className
                )
            }
        }
    }

    @Test
    fun `clicking image in preview opens ShowImageDetailsActivity`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // The WebView only exists in preview mode.
                assertTrue(screenRoot(activity).clickOnTag("btn_preview_mode"))
                idle()
                val webView = requireNotNull(activity.previewWebView)

                val url = RichTextEditorView.buildImageTapUrl("https://example.com/pic.png")
                val handled = Shadows.shadowOf(webView).webViewClient
                    .shouldOverrideUrlLoading(webView, url)
                val intent = shadowOf(activity).nextStartedActivity

                assertTrue(handled)
                assertNotNull(intent)
                assertEquals(
                    ShowImageDetailsActivity::class.java.name,
                    requireNotNull(intent).component?.className
                )
                assertEquals("pic.png", requireNotNull(intent).getStringExtra(ImageDetailsIntentContract.EXTRA_NAME))
                assertEquals("https://example.com/pic.png", requireNotNull(intent).getStringExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION))
                assertEquals(ImageDetailsIntentContract.SOURCE_NETWORK, requireNotNull(intent).getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE))
                assertEquals("https://example.com/pic.png", requireNotNull(intent).getStringExtra(ImageDetailsIntentContract.EXTRA_URL))
            }
        }
    }
}
