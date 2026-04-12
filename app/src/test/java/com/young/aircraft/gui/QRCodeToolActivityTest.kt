package com.young.aircraft.gui

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QRCodeToolActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun invokeOnScanResult(activity: QRCodeToolActivity, text: String) {
        val method = QRCodeToolActivity::class.java
            .getDeclaredMethod("onScanResult", String::class.java)
        method.isAccessible = true
        method.invoke(activity, text)
    }

    private fun getLatestAlertDialog(): AlertDialog? =
        ShadowDialog.getLatestDialog() as? AlertDialog

    // ── Activity lifecycle ───────────────────────────────────

    @Test
    fun `activity launches successfully`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_back).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    // ── Initial layout state ─────────────────────────────────

    @Test
    fun `initial state shows ScrollView and hides camera views`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scrollContent = activity.findViewById<ScrollView>(R.id.scroll_content)
                val surfaceCamera = activity.findViewById<SurfaceView>(R.id.surface_camera)
                val scanStatus = activity.findViewById<TextView>(R.id.tv_scan_status)
                val ivQrCode = activity.findViewById<ImageView>(R.id.iv_qr_code)

                assertEquals(View.VISIBLE, scrollContent.visibility)
                assertEquals(View.GONE, surfaceCamera.visibility)
                assertEquals(View.GONE, scanStatus.visibility)
                assertEquals(View.VISIBLE, ivQrCode.visibility)
            }
        }
    }

    @Test
    fun `SurfaceView is not inside ScrollView`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surfaceCamera = activity.findViewById<SurfaceView>(R.id.surface_camera)
                val scrollContent = activity.findViewById<ScrollView>(R.id.scroll_content)

                var parent = surfaceCamera.parent
                while (parent != null) {
                    assertNotSame(
                        "SurfaceView must not be a descendant of ScrollView",
                        scrollContent, parent
                    )
                    parent = (parent as? View)?.parent
                }
            }
        }
    }

    @Test
    fun `scan status TextView is not inside ScrollView`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scanStatus = activity.findViewById<TextView>(R.id.tv_scan_status)
                val scrollContent = activity.findViewById<ScrollView>(R.id.scroll_content)

                var parent = scanStatus.parent
                while (parent != null) {
                    assertNotSame(
                        "Scan status must not be a descendant of ScrollView",
                        scrollContent, parent
                    )
                    parent = (parent as? View)?.parent
                }
            }
        }
    }

    // ── Generate QR Code ─────────────────────────────────────

    @Test
    fun `generate with empty content shows toast`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                assertEquals(
                    context.getString(R.string.qr_code_tool_no_content),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `generate with English content sets ImageView bitmap`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("Hello World")

                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val iv = activity.findViewById<ImageView>(R.id.iv_qr_code)
                assertEquals(View.VISIBLE, iv.visibility)
                assertNotNull(iv.drawable)
            }
        }
    }

    @Test
    fun `generate with Chinese content sets ImageView bitmap`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("你好世界")

                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val iv = activity.findViewById<ImageView>(R.id.iv_qr_code)
                assertEquals(View.VISIBLE, iv.visibility)
                assertNotNull(iv.drawable)
            }
        }
    }

    @Test
    fun `generate with mixed Chinese English content succeeds`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("Aircraft 飞机大战 v1.2.6")

                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val iv = activity.findViewById<ImageView>(R.id.iv_qr_code)
                assertEquals(View.VISIBLE, iv.visibility)
                assertNotNull(iv.drawable)
            }
        }
    }

    @Test
    fun `generate ensures ScrollView is visible and camera views are hidden`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("test content")

                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                assertEquals(View.VISIBLE, activity.findViewById<ScrollView>(R.id.scroll_content).visibility)
                assertEquals(View.GONE, activity.findViewById<SurfaceView>(R.id.surface_camera).visibility)
                assertEquals(View.GONE, activity.findViewById<TextView>(R.id.tv_scan_status).visibility)
            }
        }
    }

    // ── Scan button label ────────────────────────────────────

    @Test
    fun `scan button initially shows scan label`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnScan = activity.findViewById<TextView>(R.id.btn_scan_qr)
                assertEquals(
                    context.getString(R.string.qr_code_tool_scan_button),
                    btnScan.text.toString()
                )
            }
        }
    }

    // ── Scan result dialog ───────────────────────────────────

    @Test
    fun `onScanResult shows dialog`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "https://example.com")

                val dialog = getLatestAlertDialog()
                assertNotNull("Dialog should be shown", dialog)
                assertTrue(dialog!!.isShowing)
            }
        }
    }

    @Test
    fun `onScanResult dialog shows scanned text`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "Hello QR")

                val dialog = getLatestAlertDialog()!!
                val messageView = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals("Hello QR", messageView?.text.toString())
            }
        }
    }

    @Test
    fun `onScanResult dialog has OK and Copy buttons`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "test")

                val dialog = getLatestAlertDialog()!!
                assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE))

                val copyButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                assertNotNull("Copy button should exist", copyButton)
                assertEquals(
                    context.getString(R.string.qr_code_tool_copy),
                    copyButton.text.toString()
                )
            }
        }
    }

    private fun clickDialogButton(dialog: AlertDialog, which: Int) {
        val button = dialog.getButton(which)
        val alertField = AlertDialog::class.java.getDeclaredField("mAlert")
        alertField.isAccessible = true
        val alertController = alertField.get(dialog)
        val field = alertController.javaClass.getDeclaredField("mButtonHandler")
        field.isAccessible = true
        val mButtonHandler = field.get(alertController) as View.OnClickListener
        mButtonHandler.onClick(button)
        // The ButtonHandler posts a Message — flush the main looper to process it
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `clicking Copy button copies text to clipboard`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "clipboard test 你好")

                val dialog = getLatestAlertDialog()!!
                clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL)

                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals("clipboard test 你好", clipboard.primaryClip?.getItemAt(0)?.text)
            }
        }
    }

    @Test
    fun `clicking Copy button shows copied toast`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "toast test")

                val dialog = getLatestAlertDialog()!!
                clickDialogButton(dialog, DialogInterface.BUTTON_NEUTRAL)

                assertEquals(
                    context.getString(R.string.qr_code_tool_copied),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `onScanResult dialog shows Chinese text correctly`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "二维码扫描结果")

                val dialog = getLatestAlertDialog()!!
                val messageView = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals("二维码扫描结果", messageView?.text.toString())
            }
        }
    }

    // ── Navigation from Settings ─────────────────────────────

    @Test
    fun `QR code tool row in Settings navigates to QRCodeToolActivity`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.row_qr_code_tool).performClick()
                val intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(
                    QRCodeToolActivity::class.java.name,
                    intent.component?.className
                )
            }
        }
    }

    // ── Editor setup ─────────────────────────────────────────

    @Test
    fun `editor has hint text set`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                assertEquals(
                    context.getString(R.string.qr_code_tool_input_hint),
                    editor.editor.hint.toString()
                )
            }
        }
    }
}
