package com.young.aircraft.gui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.young.aircraft.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceInfoActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DeviceInfoActivity>()

    @Test
    fun `screen renders header title and sections`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()

        composeRule.onNodeWithText(activity.getString(R.string.title_activity_device_info))
            .assertTextEquals(activity.getString(R.string.title_activity_device_info))

        composeRule.onNodeWithTag("device_info_scroll")
            .performScrollToNode(hasText(activity.getString(R.string.device_info_section_resources)))
        composeRule.onNodeWithText(activity.getString(R.string.device_info_section_resources))
            .assertExists()

        composeRule.onNodeWithTag("device_info_scroll")
            .performScrollToNode(hasText(activity.getString(R.string.device_info_section_system)))
        composeRule.onNodeWithText(activity.getString(R.string.device_info_section_system))
            .assertExists()
    }

    @Test
    fun `back button finishes activity`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.history_cancel))
            .performClick()
        assertTrue(composeRule.activity.isFinishing)
    }

    // ── Foldable System Info layout ───────────────────────────────────

    @Test
    fun `system info items stack vertically when folded`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        val (resolution, boot) = systemInfoBounds()
        assertTrue("boot time should sit below screen info", boot.top >= resolution.bottom)
    }

    @Test
    fun `system info items share one row when foldable unfolded`() {
        val activity = composeRule.activity
        ReflectionHelpers.getField<MutableState<Boolean>>(activity, "systemInfoWide").value = true
        composeRule.waitForIdle()
        val (resolution, boot) = systemInfoBounds()
        assertEquals(resolution.top, boot.top, 0.5f)
    }

    private fun systemInfoBounds(): Pair<Rect, Rect> {
        val activity = composeRule.activity
        composeRule.onNodeWithTag("device_info_scroll")
            .performScrollToNode(hasText(activity.getString(R.string.device_info_boot_time)))
        val resolution = composeRule.onNodeWithText(activity.getString(R.string.device_info_screen_resolution))
            .fetchSemanticsNode().boundsInRoot
        val boot = composeRule.onNodeWithText(activity.getString(R.string.device_info_boot_time))
            .fetchSemanticsNode().boundsInRoot
        return resolution to boot
    }

    // ── Uptime format unit tests ──────────────────────────────────────

    @Test
    fun `formatUptime under 1 hour shows mm ss`() {
        // 45 minutes 30 seconds = 2730 seconds
        val result = formatUptimeForTest(2730L * 1000)
        assertEquals("45:30", result)
    }

    @Test
    fun `formatUptime exactly 0 shows 00 00`() {
        val result = formatUptimeForTest(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatUptime under 1 hour boundary shows 59 59`() {
        // 59 minutes 59 seconds = 3599 seconds
        val result = formatUptimeForTest(3599L * 1000)
        assertEquals("59:59", result)
    }

    @Test
    fun `formatUptime exactly 1 hour shows hh mm ss`() {
        // 1 hour = 3600 seconds
        val result = formatUptimeForTest(3600L * 1000)
        assertEquals("01:00:00", result)
    }

    @Test
    fun `formatUptime 5 hours 30 minutes 15 seconds`() {
        val ms = (5 * 3600L + 30 * 60 + 15) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("05:30:15", result)
    }

    @Test
    fun `formatUptime 23 hours 59 minutes 59 seconds`() {
        val ms = (23 * 3600L + 59 * 60 + 59) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("23:59:59", result)
    }

    @Test
    fun `formatUptime 1 day shows day format`() {
        // 1 day 2 hours 3 minutes 4 seconds
        val ms = (1 * 86400L + 2 * 3600 + 3 * 60 + 4) * 1000
        val result = formatUptimeForTest(ms)
        // Uses string resource format: "%1$d day %2$02d:%3$02d:%4$02d"
        assertEquals("1 day 02:03:04", result)
    }

    @Test
    fun `formatUptime 29 days shows day format`() {
        val ms = (29 * 86400L + 14 * 3600 + 25 * 60 + 30) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("29 day 14:25:30", result)
    }

    @Test
    fun `formatUptime 30 days shows month format`() {
        // 30 days = 1 month 0 days
        val ms = (30 * 86400L + 3 * 3600 + 20 * 60 + 0) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("1 Month 0 day 03:20:00", result)
    }

    @Test
    fun `formatUptime 45 days shows month format`() {
        // 45 days = 1 month 15 days
        val ms = (45 * 86400L + 14 * 3600 + 25 * 60 + 30) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("1 Month 15 day 14:25:30", result)
    }

    @Test
    fun `formatUptime 365 days shows year format`() {
        // 365 days = 1y 0m 0d
        val ms = (365 * 86400L + 3 * 3600 + 20 * 60 + 0) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("1y 00m 0d 03:20:00", result)
    }

    @Test
    fun `formatUptime 500 days shows year format`() {
        // 500 days = 1y, remain 135d = 4m 15d
        val ms = (500 * 86400L + 5 * 3600 + 10 * 60 + 45) * 1000
        val result = formatUptimeForTest(ms)
        assertEquals("1y 04m 15d 05:10:45", result)
    }

    // ── formatBytes unit tests ────────────────────────────────────────

    @Test
    fun `formatBytes zero`() {
        assertEquals("0 B/s", formatBytesForTest(0))
    }

    @Test
    fun `formatBytes bytes range`() {
        assertEquals("500 B/s", formatBytesForTest(500))
    }

    @Test
    fun `formatBytes kilobytes range`() {
        assertEquals("1.5 KB/s", formatBytesForTest(1500))
    }

    @Test
    fun `formatBytes megabytes range`() {
        assertEquals("2.5 MB/s", formatBytesForTest(2_500_000))
    }

    @Test
    fun `formatBytes exact boundary KB`() {
        assertEquals("1.0 KB/s", formatBytesForTest(1000))
    }

    @Test
    fun `formatBytes exact boundary MB`() {
        assertEquals("1.0 MB/s", formatBytesForTest(1_000_000))
    }

    // ── Helper functions that mirror the ViewModel logic for pure unit testing ──

    companion object {
        /**
         * Pure function mirroring DeviceInfoViewModel.getUptime() logic.
         * Uses English string format resources directly for testability.
         */
        fun formatUptimeForTest(uptimeMs: Long): String {
            val totalSec = uptimeMs / 1000L
            val ss = totalSec % 60
            val mm = (totalSec / 60) % 60
            val hh = (totalSec / 3600) % 24
            val totalDays = totalSec / 86400

            return when {
                totalSec < 3600 -> {
                    String.format(Locale.US, "%02d:%02d", mm, ss)
                }
                totalDays < 1 -> {
                    val h = totalSec / 3600
                    String.format(Locale.US, "%02d:%02d:%02d", h, mm, ss)
                }
                totalDays < 30 -> {
                    String.format(Locale.US, "%d day %02d:%02d:%02d", totalDays, hh, mm, ss)
                }
                totalDays < 365 -> {
                    val months = totalDays / 30
                    val days = totalDays % 30
                    String.format(Locale.US, "%d Month %d day %02d:%02d:%02d", months, days, hh, mm, ss)
                }
                else -> {
                    val years = totalDays / 365
                    val remainDays = totalDays % 365
                    val months = remainDays / 30
                    val days = remainDays % 30
                    String.format(Locale.US, "%dy %02dm %dd %02d:%02d:%02d", years, months, days, hh, mm, ss)
                }
            }
        }

        /**
         * Pure function mirroring formatBytes() logic.
         */
        fun formatBytesForTest(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
                bytesPerSec >= 1_000 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1_000.0)
                else -> String.format(Locale.US, "%d B/s", bytesPerSec)
            }
        }
    }
}
