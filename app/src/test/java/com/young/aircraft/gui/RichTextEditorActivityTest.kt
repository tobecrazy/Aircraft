package com.young.aircraft.gui

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.ui.RichTextEditorView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RichTextEditorActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun findEditor(activity: RichTextEditorActivity): EditText =
        activity.findViewById<RichTextEditorView>(R.id.rich_editor).editor

    private fun findRichEditor(activity: RichTextEditorActivity): RichTextEditorView =
        activity.findViewById(R.id.rich_editor)

    // ── Activity lifecycle ───────────────────────────────────

    @Test
    fun `activity launches in edit mode`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editorView = findRichEditor(activity)
                val preview = activity.findViewById<View>(R.id.wv_preview)
                assertEquals(View.VISIBLE, editorView.visibility)
                assertEquals(View.GONE, preview.visibility)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_back).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    // ── Mode toggle ──────────────────────────────────────────

    @Test
    fun `clicking preview hides editor`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_preview_mode).performClick()

                assertEquals(View.GONE, findRichEditor(activity).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.wv_preview).visibility)
            }
        }
    }

    @Test
    fun `clicking edit after preview restores editor`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_preview_mode).performClick()
                activity.findViewById<View>(R.id.btn_edit_mode).performClick()

                assertEquals(View.VISIBLE, findRichEditor(activity).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.wv_preview).visibility)
            }
        }
    }

    // ── Formatting without selection shows toast ─────────────

    @Test
    fun `bold without selection shows toast`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(R.id.rich_btn_bold).performClick()
                assertEquals(
                    context.getString(R.string.rich_text_select_text),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `italic without selection shows toast`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(R.id.rich_btn_italic).performClick()
                assertEquals(
                    context.getString(R.string.rich_text_select_text),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `underline without selection shows toast`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = findEditor(activity)
                editor.setText("hello")
                editor.setSelection(0, 0)

                activity.findViewById<View>(R.id.rich_btn_underline).performClick()
                assertEquals(
                    context.getString(R.string.rich_text_select_text),
                    ShadowToast.getTextOfLatestToast()
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

                activity.findViewById<View>(R.id.rich_btn_bold).performClick()

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

                activity.findViewById<View>(R.id.rich_btn_italic).performClick()

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

                activity.findViewById<View>(R.id.rich_btn_underline).performClick()

                val spannable = editor.text as Spanned
                val spans = spannable.getSpans(0, 5, UnderlineSpan::class.java)
                assertTrue(spans.isNotEmpty())
            }
        }
    }

    // ── Markdown toggle ──────────────────────────────────────

    @Test
    fun `markdown toggle shows enabled toast`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.rich_btn_markdown).performClick()
                assertEquals(
                    context.getString(R.string.rich_text_md_on),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `markdown double toggle shows disabled toast`() {
        ActivityScenario.launch(RichTextEditorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btn = activity.findViewById<View>(R.id.rich_btn_markdown)
                btn.performClick() // ON
                btn.performClick() // OFF
                assertEquals(
                    context.getString(R.string.rich_text_md_off),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    // ── Navigation from DevelopSettings ──────────────────────

    @Test
    fun `rich text button navigates to RichTextEditorActivity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_test_rich_text).performClick()
                val intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(
                    RichTextEditorActivity::class.java.name,
                    intent.component?.className
                )
            }
        }
    }
}
