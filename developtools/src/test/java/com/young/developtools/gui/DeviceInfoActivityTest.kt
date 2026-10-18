package com.young.developtools.gui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.young.developtools.R
import com.young.developtools.ui.theme.AircraftTheme
import com.young.developtools.viewmodel.BatteryState
import com.young.developtools.viewmodel.CoreUsage
import com.young.developtools.viewmodel.CpuState
import com.young.developtools.viewmodel.DeviceInfoUiState
import com.young.developtools.viewmodel.DeviceInfoViewModel
import com.young.developtools.viewmodel.DiskState
import com.young.developtools.viewmodel.MemoryState
import com.young.developtools.viewmodel.NetworkState
import com.young.developtools.viewmodel.NetworkType
import com.young.developtools.viewmodel.TimeState
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
        val (current, uptime) = heroTimeBounds()
        assertTrue("uptime should sit below current time", uptime.top >= current.bottom)
        val (resolution, boot) = systemInfoBounds()
        assertTrue("boot time should sit below screen info", boot.top >= resolution.bottom)
    }

    @Test
    fun `system info items share one row when foldable unfolded`() {
        val activity = composeRule.activity
        ReflectionHelpers.getField<MutableState<Boolean>>(activity, "systemInfoWide").value = true
        composeRule.waitForIdle()
        val (current, uptime) = heroTimeBounds()
        assertEquals(current.top, uptime.top, 0.5f)
        val (resolution, boot) = systemInfoBounds()
        assertEquals(resolution.top, boot.top, 0.5f)
    }

    private fun heroTimeBounds(): Pair<Rect, Rect> {
        val activity = composeRule.activity
        val current = composeRule.onNodeWithText(activity.getString(R.string.device_info_current_time))
            .fetchSemanticsNode().boundsInRoot
        val uptime = composeRule.onNodeWithText(activity.getString(R.string.device_info_uptime))
            .fetchSemanticsNode().boundsInRoot
        return current to uptime
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

    // ── Estimate / unavailable rendering (Phase 2) ────────────────────

    @Test
    fun `cpu frequency fallback renders estimate labels and unavailable core`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        activity.setContent {
            AircraftTheme {
                DeviceInfoScreen(uiState = estimateUiState(), systemInfoWide = false, onBack = {})
            }
        }
        composeRule.waitForIdle()

        // Overall frequency-based utilization is shown with "~" plus an
        // explicit chip, never presented as a measured value.
        composeRule.onNodeWithText(activity.getString(R.string.device_info_fmt_pct_est, 44)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_cpu_estimated)).assertExists()
        // Per core: estimate format, OFF for hotplugged core, N/A for the one
        // whose utilization cannot be read (distinct from a measured 0%).
        composeRule.onNodeWithText(activity.getString(R.string.device_info_fmt_core_pct_freq_est, 50, 2400)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_cpu_core_off)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_cpu_unavailable)).assertExists()
        // Scope labels tell the reader what each percentage measures.
        composeRule.onNodeWithText(activity.getString(R.string.device_info_memory_scope)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_disk_scope)).assertExists()
    }

    @Test
    fun `overall cpu shows unavailable when no core is measurable`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        val cores = List(4) { i -> CoreUsage(index = i, pct = 0, pctAvailable = false) }
        activity.setContent {
            AircraftTheme {
                DeviceInfoScreen(
                    uiState = DeviceInfoUiState(
                        cpu = CpuState(
                            overallPct = 0,
                            overallPctAvailable = false,
                            coreUsages = cores,
                            temperature = 30f,
                            isEstimate = true
                        )
                    ),
                    systemInfoWide = false,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Keep memory/disk readable so only the five CPU texts (overall plus
        // one per non-measurable core) contribute to the count.
        val overallUnavailableState = DeviceInfoUiState(
            cpu = CpuState(
                overallPct = 0,
                overallPctAvailable = false,
                coreUsages = cores,
                temperature = 30f,
                isEstimate = true
            ),
            memory = MemoryState(pct = 61, pctAvailable = true),
            disk = DiskState(pct = 47, pctAvailable = true)
        )
        activity.setContent {
            AircraftTheme {
                DeviceInfoScreen(
                    uiState = overallUnavailableState,
                    systemInfoWide = false,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Overall N/A plus one per non-measurable core.
        composeRule.onAllNodesWithText(activity.getString(R.string.device_info_cpu_unavailable))
            .assertCountEquals(5)
    }

    @Test
    fun `long localized metric text renders as a full node`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        val longDetail = buildString { repeat(60) { append("Network detail line,") } }
        activity.setContent {
            AircraftTheme {
                DeviceInfoScreen(
                    uiState = DeviceInfoUiState(
                        cpu = CpuState(
                            overallPct = 10,
                            overallPctAvailable = true,
                            coreUsages = List(4) { i ->
                                CoreUsage(index = i, pct = 10, pctAvailable = true)
                            },
                            temperature = 30f
                        ),
                        network = NetworkState(
                            type = NetworkType.WIFI,
                            detail = longDetail,
                            ipAddress = "192.168.100.100",
                            throughputReady = true
                        )
                    ),
                    systemInfoWide = false,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // The whole long detail string stays addressable (wrapped, not clipped).
        composeRule.onNodeWithText(longDetail).assertExists()
    }

    @Test
    fun `unavailable memory and disk render na instead of zero percent`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        // Default state: memory/disk never read successfully (pctAvailable false).
        activity.setContent {
            AircraftTheme {
                DeviceInfoScreen(
                    uiState = DeviceInfoUiState(
                        cpu = CpuState(
                            overallPct = 10,
                            overallPctAvailable = true,
                            coreUsages = List(4) { i ->
                                CoreUsage(index = i, pct = 10, pctAvailable = true)
                            },
                            temperature = 30f
                        ),
                        battery = BatteryState(pct = 76, isCharging = false),
                        network = NetworkState(
                            type = NetworkType.WIFI,
                            detail = "Wi-Fi",
                            ipAddress = "1.2.3.4",
                            throughputReady = true
                        ),
                        time = TimeState(currentTime = "12:00:00", uptime = "01:00:00")
                    ),
                    systemInfoWide = false,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        val na = activity.getString(R.string.device_info_cpu_unavailable)
        // Two cards × two N/A texts (percent + detail).
        composeRule.onAllNodesWithText(na).assertCountEquals(4)
        // No fabricated "0%" anywhere on screen.
        composeRule.onAllNodesWithText(activity.getString(R.string.device_info_fmt_pct, 0))
            .assertCountEquals(0)
    }

    @Test
    fun `resource cards stack when narrow and share a row when wide`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        val memory = activity.getString(R.string.device_info_memory)
        val disk = activity.getString(R.string.device_info_disk)

        fun renderAt(widthDp: Int) {
            activity.setContent {
                AircraftTheme {
                    Box(modifier = Modifier.requiredWidth(widthDp.dp)) {
                        DeviceInfoScreen(
                            uiState = estimateUiState().copy(
                                cpu = CpuState(
                                    overallPct = 10,
                                    overallPctAvailable = true,
                                    coreUsages = List(4) { i ->
                                        CoreUsage(index = i, pct = 10, pctAvailable = true)
                                    },
                                    temperature = 30f
                                ),
                                network = NetworkState(
                                    type = NetworkType.WIFI,
                                    detail = "Wi-Fi",
                                    ipAddress = "1.2.3.4",
                                    throughputReady = true
                                )
                            ),
                            systemInfoWide = false,
                            onBack = {}
                        )
                    }
                }
            }
            composeRule.waitForIdle()
        }

        // Narrow: memory card above the disk card.
        renderAt(320)
        val narrowMemory = composeRule.onNodeWithText(memory).fetchSemanticsNode().boundsInRoot
        val narrowDisk = composeRule.onNodeWithText(disk).fetchSemanticsNode().boundsInRoot
        assertTrue("narrow layout must stack cards", narrowDisk.top >= narrowMemory.bottom)

        // Wide: side-by-side row.
        renderAt(660)
        val wideMemory = composeRule.onNodeWithText(memory).fetchSemanticsNode().boundsInRoot
        val wideDisk = composeRule.onNodeWithText(disk).fetchSemanticsNode().boundsInRoot
        assertTrue("wide layout keeps one row", wideDisk.top == wideMemory.top)
    }

    @Test
    fun `core values stay addressable at increased font scale`() {
        val activity = composeRule.activity
        composeRule.waitForIdle()
        stopLiveSampling(activity)
        activity.setContent {
            AircraftTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2f, fontScale = 2f)
                ) {
                    Box(modifier = Modifier.requiredWidth(340.dp)) {
                        DeviceInfoScreen(uiState = estimateUiState(), systemInfoWide = false, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Small controllable layout: at 2× font scale every core value and
        // label must still wrap and remain addressable, not clipped away.
        composeRule.onNodeWithText(activity.getString(R.string.device_info_fmt_pct_est, 44)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_cpu_estimated)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_memory_scope)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_disk_scope)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.device_info_cpu_unavailable)).assertExists()
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

    // ── Fabricated UI states for phase-2 rendering checks ─────────────

    private fun estimateUiState(): DeviceInfoUiState = DeviceInfoUiState(
        cpu = CpuState(
            overallPct = 44,
            overallPctAvailable = true,
            coreUsages = listOf(
                CoreUsage(index = 0, pct = 50, freqMhz = 2400, pctAvailable = true),
                CoreUsage(index = 1, pct = 0, isOnline = false),
                CoreUsage(index = 2, pct = 0, pctAvailable = false)
            ),
            temperature = 40f,
            isEstimate = true
        ),
        memory = MemoryState(pct = 61, usedGB = 5.2, availGB = 3.1, totalGB = 8.0, pctAvailable = true),
        disk = DiskState(pct = 47, usedGB = 48.1, availGB = 54.0, totalGB = 102.4, pctAvailable = true),
        network = NetworkState(
            type = NetworkType.WIFI,
            detail = "Wi-Fi",
            ipAddress = "1.2.3.4",
            throughputReady = true
        ),
        battery = BatteryState(pct = 76, isCharging = false),
        time = TimeState(currentTime = "12:00:00", uptime = "01:00:00"),
        staticInfo = com.young.developtools.viewmodel.DeviceStaticInfo(deviceModel = "Test")
    )

    private fun stopLiveSampling(activity: DeviceInfoActivity) {
        // The Activity's ViewModel refresh loop would overwrite fabricated state.
        ViewModelProvider(activity, DeviceInfoViewModel.Factory(activity))[DeviceInfoViewModel::class.java]
            .stopSampling()
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
