package com.young.aircraft.gui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.viewmodel.QRCodeToolViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

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
        // Drain the looper so the sheet's ComposeView attaches and composes.
        repeat(2) { shadowOf(Looper.getMainLooper()).idle() }
    }

    private fun viewModelOf(activity: QRCodeToolActivity): QRCodeToolViewModel {
        val field = QRCodeToolActivity::class.java.getDeclaredField("viewModel")
        field.isAccessible = true
        return field.get(activity) as QRCodeToolViewModel
    }

    private fun setEditorText(activity: QRCodeToolActivity, text: String) {
        requireNotNull(activity.richEditorView).editor.setText(text)
    }

    private fun generateQr(activity: QRCodeToolActivity, content: String): SemanticsNode {
        setEditorText(activity, content)
        assertTrue(screenRoot(activity).clickOnTag("btn_generate_qr"))
        shadowOf(Looper.getMainLooper()).idle()
        return screenRoot(activity)
    }

    // Activity screen content is Compose — traverse the window's semantics tree.
    private fun screenRoot(activity: QRCodeToolActivity): SemanticsNode =
        requireNotNull(activity.window.decorView.findSemanticsOwner()).rootSemanticsNode

    // Scan-result sheet content is Compose — traverse its semantics tree.
    private fun sheetRoot(): SemanticsNode {
        val dialog = requireNotNull(ShadowDialog.getLatestDialog())
        val decorView = requireNotNull(dialog.window?.decorView)
        return requireNotNull(decorView.findSemanticsOwner()).rootSemanticsNode
    }

    private fun SemanticsNode.findNodeWithTag(tag: String): SemanticsNode? =
        findAllNodes(this).firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

    private fun SemanticsNode.hasTag(tag: String): Boolean = findNodeWithTag(tag) != null

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
                assertTrue(screenRoot(activity).clickOnTag("btn_back"))
                assertTrue(activity.isFinishing)
            }
        }
    }

    // ── Initial layout state ─────────────────────────────────

    @Test
    fun `initial state shows ready preview messaging`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val texts = findAllNodes(screenRoot(activity)).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_status_ready))
                )
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_preview_idle_title))
                )
            }
        }
    }

    @Test
    fun `initial state hides scan pane and QR overlay`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = screenRoot(activity)
                assertFalse("Scan pane must not be composed while idle", root.hasTag("btn_pick_qr"))
                assertFalse("QR image must not exist without a generated bitmap", root.hasTag("iv_qr_code"))
                assertFalse("Save button must not exist without a generated bitmap", root.hasTag("btn_save_qr"))
            }
        }
    }

    // ── Generate QR Code ─────────────────────────────────────

    @Test
    fun `generate with empty content shows snackbar`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(screenRoot(activity).clickOnTag("btn_generate_qr"))

                assertEquals(
                    context.getString(R.string.qr_code_tool_no_content),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `generate with English content shows generated preview`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "Hello World")

                val texts = findAllNodes(root).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_status_generated))
                )
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_preview_generated_title))
                )
                assertTrue("QR image should exist after generation", root.hasTag("iv_qr_code"))
            }
        }
    }

    @Test
    fun `generate with Chinese content shows generated preview`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "你好世界")

                assertTrue("QR image should exist after generation", root.hasTag("iv_qr_code"))
            }
        }
    }

    @Test
    fun `generate with mixed Chinese English content succeeds`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "Aircraft 飞机大战 v1.2.6")

                assertTrue("QR image should exist after generation", root.hasTag("iv_qr_code"))
            }
        }
    }

    @Test
    fun `preview hint includes save hint after generation`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "hint test")

                val texts = findAllNodes(root).mapNotNull { it.displayText() }
                assertTrue(
                    "Preview hint should contain save hint after generation",
                    texts.any {
                        it.contains(context.getString(R.string.qr_code_tool_save_hint))
                    }
                )
            }
        }
    }

    // ── Scan button label ────────────────────────────────────

    @Test
    fun `scan button initially shows scan label`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val texts = findAllNodes(screenRoot(activity)).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_scan_button))
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
                assertTrue(requireNotNull(dialog).isShowing)
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet shows scanned text`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "Hello QR")

                val texts = findAllNodes(sheetRoot()).mapNotNull { it.displayText() }
                assertTrue(texts.contains("Hello QR"))
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet has copy and dismiss buttons`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "test")

                val root = sheetRoot()
                assertTrue("Copy button should exist", root.hasTag("btn_copy_result"))
                assertTrue("Dismiss button should exist", root.hasTag("btn_dismiss_result"))
                val texts = findAllNodes(root).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_copy_result))
                )
            }
        }
    }

    @Test
    fun `clicking Copy button copies text to clipboard`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "clipboard test 你好")

                assertTrue(sheetRoot().clickOnTag("btn_copy_result"))

                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals("clipboard test 你好", clipboard.primaryClip?.getItemAt(0)?.text)
            }
        }
    }

    @Test
    fun `clicking Copy button shows copied snackbar`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "toast test")

                assertTrue(sheetRoot().clickOnTag("btn_copy_result"))

                assertEquals(
                    context.getString(R.string.qr_code_tool_copied),
                    snackbarText(activity)
                )
            }
        }
    }

    @Test
    fun `onScanResult bottom sheet shows Chinese text correctly`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnScanResult(activity, "二维码扫描结果")

                val texts = findAllNodes(sheetRoot()).mapNotNull { it.displayText() }
                assertTrue(texts.contains("二维码扫描结果"))
            }
        }
    }

    // ── Navigation from Settings ─────────────────────────────

    @Test
    fun `QR code tool row in Settings navigates to QRCodeToolActivity`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val title = activity.getString(R.string.qr_code_tool_title)
                val root = requireNotNull(activity.window.decorView.findSemanticsOwner()).rootSemanticsNode
                // The clickable row merges its Text children, so match by containment.
                val row = findAllNodes(root).firstOrNull { node ->
                    node.displayText()?.contains(title) == true &&
                        node.config.getOrNull(SemanticsActions.OnClick) != null
                }
                assertNotNull("Settings row for QR Code Tool not found", row)
                val onClick = requireNotNull(
                    requireNotNull(row).config.getOrNull(SemanticsActions.OnClick)
                )
                requireNotNull(onClick.action).invoke()
                val intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(
                    QRCodeToolActivity::class.java.name,
                    requireNotNull(intent).component?.className
                )
            }
        }
    }

    // ── Editor setup ─────────────────────────────────────────

    @Test
    fun `editor has hint text set`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = requireNotNull(activity.richEditorView).editor
                assertEquals(
                    context.getString(R.string.qr_code_tool_input_hint),
                    editor.hint.toString()
                )
            }
        }
    }

    // ── Pick from Gallery buttons ────────────────────────────

    @Test
    fun `idle gallery pick button has correct label`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = screenRoot(activity)
                assertTrue("Idle gallery pick button should exist", root.hasTag("btn_pick_gallery_idle"))
                val texts = findAllNodes(root).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_pick_gallery))
                )
            }
        }
    }

    @Test
    fun `scan pane exposes gallery pick button`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // The scan line is an infinite animation. With the default (unpaused)
                // choreographer every vsync request is answered inline and the callback
                // chain never terminates, so no idle/idleFor call can ever drain it.
                // Pausing makes frames fire only on bounded clock advances below.
                ShadowChoreographer.setPaused(true)
                viewModelOf(activity).startScanning()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

                val root = screenRoot(activity)
                assertTrue("Scan pane pick button should exist while scanning", root.hasTag("btn_pick_qr"))
                val texts = findAllNodes(root).mapNotNull { it.displayText() }
                assertTrue(
                    texts.contains(context.getString(R.string.qr_code_tool_pick_gallery))
                )

                // Exit the pane fully (infinite animation disposed) before teardown.
                viewModelOf(activity).stopScanning()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            }
        }
    }

    // ── Save overlay and long-press ────────────────────────────

    @Test
    fun `save and share buttons appear after generating QR`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "save button test")

                assertTrue("Save button should exist after generation", root.hasTag("btn_save_qr"))
                assertTrue("Share button should exist after generation", root.hasTag("btn_share_qr"))
            }
        }
    }

    @Test
    fun `save button click launches save file picker`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                generateQr(activity, "save icon test")

                assertTrue(screenRoot(activity).clickOnTag("btn_save_qr"))

                val intent = shadowOf(activity).nextStartedActivityForResult
                assertNotNull("File picker intent should be launched", intent)
            }
        }
    }

    @Test
    fun `long press QR image launches save file picker`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = generateQr(activity, "save picker test")

                val node = requireNotNull(root.findNodeWithTag("iv_qr_code"))
                val longClick = requireNotNull(node.config.getOrNull(SemanticsActions.OnLongClick))
                requireNotNull(longClick.action).invoke()

                val intent = shadowOf(activity).nextStartedActivityForResult
                assertNotNull("File picker intent should be launched", intent)
            }
        }
    }

    // ── decodeQrFromBitmap ──────────────────────────────────

    @Test
    fun `decodeQrFromBitmap with non-QR bitmap returns null`() {
        ActivityScenario.launch(QRCodeToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nonQrBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

                assertNull("Should return null for non-QR bitmap", viewModelOf(activity).decodeQrFromBitmap(nonQrBitmap))
            }
        }
    }
}
