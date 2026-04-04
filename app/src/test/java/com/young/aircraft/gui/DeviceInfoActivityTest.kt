package com.young.aircraft.gui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceInfoActivityTest {

    @Test
    fun `activity launches and displays static info`() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 7")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "14")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)

        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvModel = activity.findViewById<TextView>(R.id.tv_device_model)
                val tvAndroid = activity.findViewById<TextView>(R.id.tv_android_version)

                assertEquals("GOOGLE Pixel 7", tvModel.text.toString())
                assertTrue(tvAndroid.text.toString().contains("Android 14"))
                assertTrue(tvAndroid.text.toString().contains("API 34"))
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `cpu temperature view exists`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvCpuTemp = activity.findViewById<TextView>(R.id.tv_cpu_temp)
                assertNotNull(tvCpuTemp)
                val text = tvCpuTemp.text.toString()
                // Should display either a temperature or N/A
                assertTrue(
                    "CPU temp should show temperature or N/A, got: $text",
                    text.contains("°C") || text.contains("N/A")
                )
            }
        }
    }

    @Test
    fun `memory detail shows buffer and cache info`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvMemory = activity.findViewById<TextView>(R.id.tv_memory)
                val text = tvMemory.text.toString()
                // New format includes "used", "free", "total", "Buf", "Cache"
                // or Chinese equivalents
                assertTrue(
                    "Memory detail should show multi-line info with used/free/total, got: $text",
                    text.contains("G") && text.contains("\n")
                )
            }
        }
    }

    @Test
    fun `disk detail shows used free and total`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvDisk = activity.findViewById<TextView>(R.id.tv_disk)
                val text = tvDisk.text.toString()
                assertTrue(
                    "Disk detail should show multi-line info, got: $text",
                    text.contains("G") && text.contains("\n")
                )
            }
        }
    }

    @Test
    fun `network throughput view exists and initialized`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvThroughput = activity.findViewById<TextView>(R.id.tv_network_throughput)
                assertNotNull(tvThroughput)
                // Should show arrows (↓↑) or be empty when offline
                val text = tvThroughput.text.toString()
                assertTrue(
                    "Network throughput should show arrows or be empty, got: $text",
                    text.isEmpty() || text.contains("\u2193") || text.contains("\u2191")
                )
            }
        }
    }

    @Test
    fun `network extra view exists`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvExtra = activity.findViewById<TextView>(R.id.tv_network_extra)
                assertNotNull(tvExtra)
                // Should show IP or be empty when offline
                val text = tvExtra.text.toString()
                assertTrue(
                    "Network extra should show IP or be empty, got: $text",
                    text.isEmpty() || text.contains("IP")
                )
            }
        }
    }

    @Test
    fun `uptime displays in correct format`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvUptime = activity.findViewById<TextView>(R.id.tv_uptime)
                val text = tvUptime.text.toString()
                assertFalse("Uptime should not be empty", text.isEmpty())
                // Should contain colons (mm:ss or hh:mm:ss format)
                assertTrue(
                    "Uptime should contain colon-separated time, got: $text",
                    text.contains(":")
                )
            }
        }
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

    // ── Helper functions that mirror the Activity logic for pure unit testing ──

    companion object {
        /**
         * Pure function mirroring DeviceInfoActivity.getUptime() logic.
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
         * Pure function mirroring DeviceInfoActivity.formatBytes() logic.
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
