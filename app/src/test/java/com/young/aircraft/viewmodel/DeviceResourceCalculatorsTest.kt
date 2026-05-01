package com.young.aircraft.viewmodel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceResourceCalculatorsTest {

    @Test
    fun `parseCpuStatLine reads first eight scheduler counters`() {
        val result = DeviceResourceCalculators.parseCpuStatLine(
            "cpu  100 2 30 400 5 6 7 8 9 10"
        )

        assertArrayEquals(longArrayOf(100, 2, 30, 400, 5, 6, 7, 8), result)
    }

    @Test
    fun `parseCpuStatLine rejects non cpu lines`() {
        assertNull(DeviceResourceCalculators.parseCpuStatLine("intr 1 2 3"))
    }

    @Test
    fun `calcCpuUsagePercent uses delta between snapshots`() {
        val prev = longArrayOf(100, 0, 50, 800, 50, 0, 0, 0)
        val curr = longArrayOf(130, 0, 80, 860, 70, 0, 10, 0)

        assertEquals(46, DeviceResourceCalculators.calcCpuUsagePercent(prev, curr))
    }

    @Test
    fun `calcCpuUsagePercent returns null for reset counters`() {
        val prev = longArrayOf(100, 0, 50, 800, 50, 0, 0, 0)
        val curr = longArrayOf(10, 0, 5, 80, 5, 0, 0, 0)

        assertNull(DeviceResourceCalculators.calcCpuUsagePercent(prev, curr))
    }
}
