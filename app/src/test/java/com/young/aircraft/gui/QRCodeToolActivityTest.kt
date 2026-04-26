package com.young.aircraft.gui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
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
    fun `initial state shows ready preview messaging`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val heroStatus = activity.findViewById<TextView>(R.id.tv_hero_status)
                val previewTitle = activity.findViewById<TextView>(R.id.tv_preview_title)
                val placeholder = activity.findViewById<TextView>(R.id.tv_qr_placeholder)

                assertEquals(
                    context.getString(R.string.qr_code_tool_status_ready),
                    heroStatus.text.toString()
                )
                assertEquals(
                    context.getString(R.string.qr_code_tool_preview_idle_title),
                    previewTitle.text.toString()
                )
                assertEquals(View.VISIBLE, placeholder.visibility)
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

    @Test
    fun `generate updates preview state and hides placeholder`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("https://young.example/qr")

                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val heroStatus = activity.findViewById<TextView>(R.id.tv_hero_status)
                val previewTitle = activity.findViewById<TextView>(R.id.tv_preview_title)
                val placeholder = activity.findViewById<TextView>(R.id.tv_qr_placeholder)

                assertEquals(
                    context.getString(R.string.qr_code_tool_status_generated),
                    heroStatus.text.toString()
                )
                assertEquals(
                    context.getString(R.string.qr_code_tool_preview_generated_title),
                    previewTitle.text.toString()
                )
                assertEquals(View.GONE, placeholder.visibility)
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

    // ── Scan result bottom sheet ──────────────────────────────

    @Test
    fun `onScanResult shows bottom sheet`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "https://example.com")

                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull("Bottom sheet dialog should be shown", dialog)
                assertTrue(dialog!!.isShowing)
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet shows scanned text`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "Hello QR")

                val dialog = ShadowDialog.getLatestDialog()!!
                val resultText = dialog.findViewById<TextView>(R.id.tv_scan_result_text)
                assertEquals("Hello QR", resultText?.text.toString())
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet has copy and dismiss buttons`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "test")

                val dialog = ShadowDialog.getLatestDialog()!!
                val copyButton = dialog.findViewById<TextView>(R.id.btn_copy_result)
                val dismissButton = dialog.findViewById<TextView>(R.id.btn_dismiss_result)
                assertNotNull("Copy button should exist", copyButton)
                assertNotNull("Dismiss button should exist", dismissButton)
                assertEquals(
                    context.getString(R.string.qr_code_tool_copy_result),
                    copyButton?.text.toString()
                )
            }
        }
    }

    @Test
    fun `clicking Copy button copies text to clipboard`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "clipboard test 你好")

                val dialog = ShadowDialog.getLatestDialog()!!
                dialog.findViewById<TextView>(R.id.btn_copy_result)!!.performClick()

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

                val dialog = ShadowDialog.getLatestDialog()!!
                dialog.findViewById<TextView>(R.id.btn_copy_result)!!.performClick()

                assertEquals(
                    context.getString(R.string.qr_code_tool_copied),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet shows Chinese text correctly`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "二维码扫描结果")

                val dialog = ShadowDialog.getLatestDialog()!!
                val resultText = dialog.findViewById<TextView>(R.id.tv_scan_result_text)
                assertEquals("二维码扫描结果", resultText?.text.toString())
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

    // ── Pick from Gallery button ────────────────────────────

    @Test
    fun `pick button exists in layout`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnPick = activity.findViewById<TextView>(R.id.btn_pick_qr)
                assertNotNull("Pick from Gallery button should exist", btnPick)
            }
        }
    }

    @Test
    fun `pick button is inside camera container`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnPick = activity.findViewById<View>(R.id.btn_pick_qr)
                val cameraContainer = activity.findViewById<View>(R.id.camera_container)

                var parent = btnPick.parent
                var found = false
                while (parent != null) {
                    if (parent === cameraContainer) {
                        found = true
                        break
                    }
                    parent = (parent as? View)?.parent
                }
                assertTrue("Pick button must be a descendant of camera_container", found)
            }
        }
    }

    @Test
    fun `pick button has correct label`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnPick = activity.findViewById<TextView>(R.id.btn_pick_qr)
                assertEquals(
                    context.getString(R.string.qr_code_tool_pick_gallery),
                    btnPick.text.toString()
                )
            }
        }
    }

    // ── Long-press save ─────────────────────────────────────

    @Test
    fun `QR image has long click listener`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val ivQrCode = activity.findViewById<ImageView>(R.id.iv_qr_code)
                assertTrue("ivQrCode should be long clickable", ivQrCode.isLongClickable)
            }
        }
    }

    @Test
    fun `long click without generated bitmap returns false`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val ivQrCode = activity.findViewById<ImageView>(R.id.iv_qr_code)
                val consumed = ivQrCode.performLongClick()
                assertFalse("Long click should not be consumed without a generated bitmap", consumed)
            }
        }
    }

    @Test
    fun `long click after generating QR code is consumed`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("save test")
                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val ivQrCode = activity.findViewById<ImageView>(R.id.iv_qr_code)
                val consumed = ivQrCode.performLongClick()
                assertTrue("Long click should be consumed after QR generation", consumed)
            }
        }
    }

    @Test
    fun `long click after generating QR launches file picker intent`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("save picker test")
                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val ivQrCode = activity.findViewById<ImageView>(R.id.iv_qr_code)
                ivQrCode.performLongClick()

                val intent = shadowOf(activity).nextStartedActivityForResult
                assertNotNull("File picker intent should be launched", intent)
            }
        }
    }

    // ── Preview hint includes save hint after generation ─────

    @Test
    fun `preview hint includes save hint after generation`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<RichTextEditorView>(R.id.rich_editor)
                editor.editor.setText("hint test")
                activity.findViewById<View>(R.id.btn_generate_qr).performClick()

                val previewHint = activity.findViewById<TextView>(R.id.tv_preview_hint)
                assertTrue(
                    "Preview hint should contain save hint after generation",
                    previewHint.text.toString().contains(
                        context.getString(R.string.qr_code_tool_save_hint)
                    )
                )
            }
        }
    }

    // ── decodeQrFromBitmap ──────────────────────────────────

    @Test
    fun `decodeQrFromBitmap with non-QR bitmap shows invalid toast`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nonQrBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

                val method = QRCodeToolActivity::class.java
                    .getDeclaredMethod("decodeQrFromBitmap", Bitmap::class.java)
                method.isAccessible = true
                method.invoke(activity, nonQrBitmap)

                assertEquals(
                    context.getString(R.string.qr_code_tool_invalid_qr),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }
}
