package com.young.developtools.gui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.developtools.R
import com.young.developtools.viewmodel.CameraLens
import com.young.developtools.viewmodel.CameraScanMode
import com.young.developtools.viewmodel.CameraScanViewModel
import com.young.developtools.viewmodel.CardModelStatus
import com.young.developtools.viewmodel.FakeCardNumberRecognizer
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric coverage for the card-scan UI states. A fake recognizer replaces
 * AICore, the downloaded model, and the camera, so no hardware is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h920dp")
class CameraScanActivityTest {

    private lateinit var context: Context
    private lateinit var recognizer: FakeCardNumberRecognizer

    private val visa = "4111111111111111"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        recognizer = FakeCardNumberRecognizer()
        CameraScanActivity.viewModelFactoryOverride =
            CameraScanViewModel.Factory { recognizer }
    }

    @After
    fun tearDown() {
        CameraScanActivity.viewModelFactoryOverride = null
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun root(activity: CameraScanActivity): SemanticsNode =
        requireNotNull(activity.window.decorView.findSemanticsOwner()).rootSemanticsNode

    private fun viewModelOf(activity: CameraScanActivity): CameraScanViewModel {
        val field = CameraScanActivity::class.java.getDeclaredField("viewModel")
        field.isAccessible = true
        return field.get(activity) as CameraScanViewModel
    }

    private fun SemanticsNode.findNodeWithTag(tag: String): SemanticsNode? =
        findAllNodes(this).firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag) == tag
        }

    private fun SemanticsNode.hasTag(tag: String): Boolean = findNodeWithTag(tag) != null

    private fun SemanticsNode.texts(): List<String> =
        findAllNodes(this).mapNotNull { it.displayText() }

    private fun openCardMode(activity: CameraScanActivity) {
        assertTrue(root(activity).clickOnTag("btn_scan_mode_card"))
        idle()
    }

    // ── Mode switching ───────────────────────────────────────

    @Test
    fun `QR mode is the default and card mode switches the title`() {
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(CameraScanMode.QR, viewModelOf(activity).uiState.value.mode)
                assertTrue(
                    root(activity).texts().contains(context.getString(R.string.camera_scan_title))
                )

                openCardMode(activity)

                assertEquals(CameraScanMode.CARD, viewModelOf(activity).uiState.value.mode)
                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_title))
                )
            }
        }
    }

    @Test
    fun `card mode always shows the privacy guidance`() {
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)

                assertTrue(root(activity).hasTag("tv_card_privacy"))
                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_privacy))
                )
            }
        }
    }

    @Test
    fun `camera switch button toggles between rear and front`() {
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(CameraLens.BACK, viewModelOf(activity).uiState.value.lensFacing)
                assertTrue(root(activity).hasTag("btn_switch_camera"))

                assertTrue(root(activity).clickOnTag("btn_switch_camera"))
                idle()
                assertEquals(CameraLens.FRONT, viewModelOf(activity).uiState.value.lensFacing)

                assertTrue(root(activity).clickOnTag("btn_switch_camera"))
                idle()
                assertEquals(CameraLens.BACK, viewModelOf(activity).uiState.value.lensFacing)
            }
        }
    }

    // ── Model availability states ────────────────────────────

    @Test
    fun `unavailable model shows the unavailable state and no result`() {
        recognizer.status = CardModelStatus.UNAVAILABLE
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)

                assertTrue(root(activity).hasTag("btn_card_model_recovery"))
                assertTrue(
                    root(activity).texts().contains(
                        context.getString(R.string.camera_scan_card_model_unavailable)
                    )
                )
                assertFalse(root(activity).hasTag("tv_card_masked_number"))
            }
        }
    }

    @Test
    fun `download required state downloads the model and starts scanning`() {
        recognizer.status = CardModelStatus.DOWNLOAD_REQUIRED
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)

                assertTrue(
                    root(activity).texts().contains(
                        context.getString(R.string.camera_scan_card_model_download_required)
                    )
                )
                assertTrue(root(activity).clickOnTag("btn_card_model_recovery"))
                idle()

                assertEquals(1, recognizer.downloadCalls)
                assertEquals(
                    CardModelStatus.AVAILABLE,
                    viewModelOf(activity).uiState.value.cardModelStatus
                )
                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_hint))
                )
            }
        }
    }

    @Test
    fun `download failure keeps the download action with an error message`() {
        recognizer.status = CardModelStatus.DOWNLOAD_REQUIRED
        recognizer.downloadResult = false
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)
                assertTrue(root(activity).clickOnTag("btn_card_model_recovery"))
                idle()

                assertTrue(
                    root(activity).texts().contains(
                        context.getString(R.string.camera_scan_card_download_failed)
                    )
                )
                assertTrue(root(activity).hasTag("btn_card_model_recovery"))
            }
        }
    }

    @Test
    fun `local-only mode scans with OCR and shows the fallback notice`() {
        recognizer.status = CardModelStatus.LOCAL_ONLY
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)

                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_local_only))
                )

                viewModelOf(activity).onCardFrameText("CARD 4111 1111 1111 1111")
                idle()

                assertTrue(root(activity).hasTag("tv_card_masked_number"))
                assertTrue(root(activity).texts().contains("•••• •••• •••• 1111"))
                assertEquals(0, recognizer.extractCalls)
            }
        }
    }

    // ── Scanning and results ─────────────────────────────────

    @Test
    fun `valid card shows a masked PAN and never renders the full number`() {
        recognizer.candidate = "4111 1111 1111 1111"
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)
                viewModelOf(activity).onCardFrameText("CARD 4111-1111 1111 1111 EXP 12/30")
                idle()

                val texts = root(activity).texts()
                assertTrue(root(activity).hasTag("tv_card_masked_number"))
                assertTrue(texts.contains("•••• •••• •••• 1111"))
                assertFalse(texts.any { it.contains(visa) })
                assertTrue(root(activity).hasTag("btn_copy_card_number"))
            }
        }
    }

    @Test
    fun `scanning state shows progress text while inference runs`() {
        val gate = CompletableDeferred<Unit>()
        recognizer.candidate = visa
        recognizer.gate = gate
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)
                viewModelOf(activity).onCardFrameText("CARD $visa")
                idle()

                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_scanning))
                )

                gate.complete(Unit)
                idle()

                assertTrue(root(activity).hasTag("tv_card_masked_number"))
            }
        }
    }

    @Test
    fun `rescan clears the masked result and resumes the hint`() {
        recognizer.candidate = visa
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)
                viewModelOf(activity).onCardFrameText("CARD $visa")
                idle()
                assertTrue(root(activity).hasTag("tv_card_masked_number"))

                assertTrue(root(activity).clickOnTag("btn_card_rescan"))
                idle()

                assertFalse(root(activity).hasTag("tv_card_masked_number"))
                assertTrue(
                    root(activity).texts()
                        .contains(context.getString(R.string.camera_scan_card_hint))
                )
            }
        }
    }

    @Test
    fun `invalid number shows an error and rescan recovers`() {
        recognizer.candidate = "4111111111111112"
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openCardMode(activity)
                viewModelOf(activity).onCardFrameText("CARD 4111 1111 1111 1112")
                idle()

                assertTrue(
                    root(activity).texts().contains(
                        context.getString(R.string.camera_scan_card_no_valid_number)
                    )
                )

                assertTrue(root(activity).clickOnTag("btn_card_error_rescan"))
                idle()
                assertFalse(root(activity).hasTag("tv_card_masked_number"))

                recognizer.candidate = visa
                viewModelOf(activity).onCardFrameText("CARD $visa")
                idle()
                assertTrue(root(activity).hasTag("tv_card_masked_number"))
            }
        }
    }

    // ── Clipboard privacy ────────────────────────────────────

    @Test
    fun `copying the full PAN marks the clipboard entry sensitive`() {
        ActivityScenario.launch(CameraScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val method = CameraScanActivity::class.java
                    .getDeclaredMethod("copyCardNumber", String::class.java)
                method.isAccessible = true
                method.invoke(activity, visa)

                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                assertEquals(visa, clipboard.primaryClip?.getItemAt(0)?.text)
                assertTrue(
                    clipboard.primaryClipDescription?.extras
                        ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
                )
                assertEquals(
                    context.getString(R.string.camera_scan_card_copied),
                    snackbarText(activity)
                )
            }
        }
    }
}
