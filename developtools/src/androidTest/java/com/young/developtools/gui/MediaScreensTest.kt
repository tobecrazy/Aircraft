package com.young.developtools.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.young.developtools.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the media, sensor and document tool pages. None of
 * these request a runtime permission on entry, so each page renders its own
 * permission or empty state without a system dialog on top.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MediaScreensTest {

    @Test
    fun cameraScanScreenRenders() {
        withScreen(CameraScanActivity::class.java) { screen ->
            assertTrue("live scan title", screen.waitForText(R.string.camera_scan_title))
        }
    }

    @Test
    fun qrCodeToolScreenRenders() {
        withScreen(QRCodeToolActivity::class.java) { screen ->
            assertTrue("qr tool title", screen.waitForText(R.string.qr_code_tool_title))
        }
    }

    @Test
    fun flashlightScreenRenders() {
        withScreen(FlashlightActivity::class.java) { screen ->
            assertTrue("flashlight title", screen.waitForText(R.string.flashlight_title))
        }
    }

    @Test
    fun pdfReaderScreenShowsEmptyState() {
        withScreen(PdfReaderActivity::class.java) { screen ->
            // No document is attached, so the reader must land on its picker hint.
            assertTrue("pdf empty hint", screen.waitForText(R.string.pdf_reader_empty_hint))
        }
    }

    @Test
    fun pdfReaderShowsOpenAction() {
        withScreen(PdfReaderActivity::class.java) { screen ->
            assertTrue("pdf empty hint", screen.waitForText(R.string.pdf_reader_empty_hint))
            // The empty state must still offer the picker instead of dead-ending.
            assertTrue("pdf picker action", screen.waitForText(R.string.pdf_reader_choose_file))
        }
    }

    @Test
    fun deviceInfoShowsCpuAndMemorySections() {
        withScreen(DeviceInfoActivity::class.java) { screen ->
            assertTrue("device info title", screen.waitForText(R.string.title_activity_device_info))
            assertTrue("cpu section", screen.waitForText(R.string.device_info_cpu))
            assertTrue("memory section", screen.waitForText(R.string.device_info_memory))
        }
    }

    @Test
    fun richTextEditorScreenRenders() {
        withScreen(RichTextEditorActivity::class.java) { screen ->
            assertTrue("rich text title", screen.waitForText(R.string.rich_text_editor_title))
        }
    }

    @Test
    fun deviceInfoScreenRenders() {
        withScreen(DeviceInfoActivity::class.java) { screen ->
            assertTrue("device info title", screen.waitForText(R.string.title_activity_device_info))
        }
    }
}
