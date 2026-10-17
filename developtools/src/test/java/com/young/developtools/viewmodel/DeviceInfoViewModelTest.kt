package com.young.developtools.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused tests for DeviceInfoViewModel phase-2 semantics: unavailable source
 * readings must keep the last valid value, the frequency-based CPU fallback
 * must be labeled as an estimate, and sampling must follow visibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceInfoViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()

    // ── Source-unavailable behavior ───────────────────────────────────

    @Test
    fun `merge keeps previous readings when a source is unavailable`() {
        val viewModel = DeviceInfoViewModel(context)
        val current = DeviceInfoUiState(
            cpu = CpuState(overallPct = 30, coreUsages = listOf(CoreUsage(index = 0, pct = 30))),
            memory = MemoryState(pct = 55),
            disk = DiskState(pct = 40),
            network = NetworkState(type = NetworkType.WIFI),
            time = TimeState(currentTime = "old", uptime = "01:00")
        )

        val merged = viewModel.mergeDynamicReadings(
            current,
            DeviceInfoViewModel.DynamicReadings(
                cpu = null,
                memory = null,
                disk = null,
                network = null,
                time = TimeState(currentTime = "new", uptime = "02:00")
            )
        )

        assertEquals(30, merged.cpu.overallPct)
        assertEquals(current.memory, merged.memory)
        assertEquals(current.disk, merged.disk)
        assertEquals(NetworkType.WIFI, merged.network.type)
        // Time always refreshes.
        assertEquals("new", merged.time.currentTime)
    }

    @Test
    fun `unavailable disk source keeps the last valid reading`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.diskStateReader = { DiskState(pct = 42, pctAvailable = true) }

        viewModel.refreshDynamicInfoNow()
        assertEquals(42, viewModel.uiState.value.disk.pct)
        assertTrue(viewModel.uiState.value.disk.pctAvailable)

        // Source starts failing: no fabricated zeros.
        viewModel.diskStateReader = { null }
        viewModel.refreshDynamicInfoNow()
        assertEquals(42, viewModel.uiState.value.disk.pct)
        assertTrue(viewModel.uiState.value.disk.pctAvailable)
    }

    @Test
    fun `disk stays unavailable after the first read fails`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.diskStateReader = { null }

        viewModel.refreshDynamicInfoNow()

        val disk = viewModel.uiState.value.disk
        assertFalse(disk.pctAvailable)
    }

    @Test
    fun `memory stays unavailable after the first read fails`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.memoryStateReader = { null }

        viewModel.refreshDynamicInfoNow()

        val memory = viewModel.uiState.value.memory
        assertFalse(memory.pctAvailable)
    }

    @Test
    fun `unexpected source exception keeps the previous reading`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.memoryStateReader = { MemoryState(pct = 61, pctAvailable = true) }

        viewModel.refreshDynamicInfoNow()
        assertEquals(61, viewModel.uiState.value.memory.pct)
        assertTrue(viewModel.uiState.value.memory.pctAvailable)

        viewModel.memoryStateReader = { error("blocked") }
        viewModel.refreshDynamicInfoNow()
        assertEquals(61, viewModel.uiState.value.memory.pct)
        assertTrue(viewModel.uiState.value.memory.pctAvailable)
    }

    // ── CPU fallback estimate semantics ───────────────────────────────

    @Test
    fun `frequency estimate calculator validates inputs`() {
        assertEquals(50, DeviceResourceCalculators.calcCpuFreqEstimatePercent(1_200_000L, 2_400_000L))
        assertEquals(100, DeviceResourceCalculators.calcCpuFreqEstimatePercent(5_000_000L, 2_400_000L))
        assertEquals(0, DeviceResourceCalculators.calcCpuFreqEstimatePercent(0L, 2_400_000L))
        assertNull(DeviceResourceCalculators.calcCpuFreqEstimatePercent(1_000L, 0L))
        assertNull(DeviceResourceCalculators.calcCpuFreqEstimatePercent(-1L, 2_400_000L))
    }

    @Test
    fun `frequency fallback marks cpu as estimate and unknown cores as unavailable`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.procStatReader = { null }
        viewModel.thermalReader = { null }
        viewModel.coreOnlineReader = { it < 3 }
        viewModel.maxFreqReader = { 2_400_000L }
        viewModel.currentFreqReader = { i -> if (i == 2) null else 1_200_000L }

        viewModel.refreshDynamicInfoNow()

        val cpu = viewModel.uiState.value.cpu
        assertTrue(cpu.isEstimate)
        assertTrue(cpu.overallPctAvailable)
        // Cores 0 and 1 estimate at 50%; the offline core is excluded.
        assertEquals(50, cpu.overallPct)
        val cores = cpu.coreUsages
        assertTrue("expected at least 4 core entries", cores.size >= 4)
        assertEquals(50, cores[0].pct)
        assertEquals(50, cores[1].pct)
        // Online core without a readable frequency: unavailable, never 0 busy.
        assertFalse(cores[2].pctAvailable)
        assertTrue(cores[2].isOnline)
        assertEquals(0, cores[2].pct)
        // Cores beyond the first three are offline.
        assertTrue(cores.drop(3).all { !it.isOnline })
    }

    @Test
    fun `frequency fallback with no measurable core reports overall unavailable`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.procStatReader = { null }
        viewModel.thermalReader = { null }
        viewModel.coreOnlineReader = { true }
        viewModel.maxFreqReader = { 0L }
        viewModel.currentFreqReader = { null }

        viewModel.refreshDynamicInfoNow()

        val cpu = viewModel.uiState.value.cpu
        assertTrue(cpu.isEstimate)
        assertFalse(cpu.overallPctAvailable)
    }

    @Test
    fun `proc stat readings are measured not estimated`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.thermalReader = { null }
        viewModel.currentFreqReader = { null }
        viewModel.coreOnlineReader = { false }
        viewModel.maxFreqReader = { 0L }
        // Fabricated /proc/stat snapshot so the test does not depend on the
        // host filesystem exposing one.
        val snapshot = DeviceInfoViewModel.CpuSnapshot(
            total = LongArray(8) { 1000L },
            perCore = listOf(LongArray(8) { 1000L })
        )
        viewModel.procStatReader = { snapshot }

        viewModel.refreshDynamicInfoNow()

        val cpu = viewModel.uiState.value.cpu
        assertFalse(cpu.isEstimate)
    }

    @Test
    fun `unchanged counter interval is unavailable until measured then previous is retained`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.thermalReader = { null }
        viewModel.currentFreqReader = { null }
        viewModel.coreOnlineReader = { true }
        viewModel.maxFreqReader = { 0L }

        // First sample: no previous anchor, counters static. Second sample:
        // counters advance by 100 ticks in every field. Third sample: counters
        // static again (zero delta → null calculation).
        val ticksA = LongArray(8) { 10_000L }
        val ticksB = LongArray(8) { 10_100L }
        var readCount = 0
        viewModel.procStatReader = {
            readCount++
            val ticks = when {
                readCount <= 1 -> ticksA
                readCount == 2 -> ticksB
                else -> ticksB
            }
            DeviceInfoViewModel.CpuSnapshot(ticks, listOf(ticks))
        }

        // Pass 1: no delta and no previous valid reading → unavailable, not 0%.
        viewModel.refreshDynamicInfoNow()
        var cpu = viewModel.uiState.value.cpu
        assertFalse(cpu.isEstimate)
        assertFalse(cpu.overallPctAvailable)
        assertFalse(cpu.coreUsages[0].pctAvailable)

        // Pass 2: real delta → measured values.
        viewModel.refreshDynamicInfoNow()
        cpu = viewModel.uiState.value.cpu
        assertTrue(cpu.overallPctAvailable)
        assertTrue(cpu.coreUsages[0].pctAvailable)
        // diffTotal = 8*100 = 800 ticks, idle+iowait delta = 200 → 75%.
        assertEquals(75, cpu.overallPct)
        assertEquals(75, cpu.coreUsages[0].pct)

        // Pass 3: zero delta → null → keep the previous valid reading.
        viewModel.refreshDynamicInfoNow()
        cpu = viewModel.uiState.value.cpu
        assertTrue(cpu.overallPctAvailable)
        assertEquals(75, cpu.overallPct)
        assertTrue(cpu.coreUsages[0].pctAvailable)
        assertEquals(75, cpu.coreUsages[0].pct)
    }

    @Test
    fun `proc stat recovery without a delta keeps the estimate labeling`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.thermalReader = { null }

        // Phase 1: /proc/stat unreadable → frequency fallback produces estimates.
        viewModel.procStatReader = { null }
        viewModel.coreOnlineReader = { true }
        viewModel.maxFreqReader = { 2_400_000L }
        viewModel.currentFreqReader = { 1_200_000L }
        viewModel.refreshDynamicInfoNow()
        var cpu = viewModel.uiState.value.cpu
        assertTrue(cpu.isEstimate)
        assertTrue(cpu.overallPctAvailable)
        assertEquals(50, cpu.overallPct)

        // Phase 2: /proc/stat becomes readable but has no delta to anchor
        // against — the retained value keeps its estimate labeling.
        val staticTicks = LongArray(8) { 10_000L }
        viewModel.procStatReader = { DeviceInfoViewModel.CpuSnapshot(staticTicks, listOf(staticTicks)) }
        viewModel.refreshDynamicInfoNow()
        cpu = viewModel.uiState.value.cpu
        assertTrue("retained estimate must stay labeled as an estimate", cpu.isEstimate)
        assertTrue(cpu.overallPctAvailable)
        assertEquals(50, cpu.overallPct)
        assertTrue(cpu.coreUsages[0].pctAvailable)
        assertEquals(50, cpu.coreUsages[0].pct)

        // Phase 3: counters advance → real measured value.
        val advancedTicks = LongArray(8) { 10_100L }
        viewModel.procStatReader = { DeviceInfoViewModel.CpuSnapshot(advancedTicks, listOf(advancedTicks)) }
        viewModel.currentFreqReader = { null }
        viewModel.maxFreqReader = { 0L }
        viewModel.refreshDynamicInfoNow()
        cpu = viewModel.uiState.value.cpu
        assertFalse(cpu.isEstimate)
        assertTrue(cpu.overallPctAvailable)
        assertEquals(75, cpu.overallPct)
        assertTrue(cpu.coreUsages[0].pctAvailable)
        assertEquals(75, cpu.coreUsages[0].pct)
    }

    // ── Sampling lifecycle transitions ─────────────────────────────────

    @Test
    fun `sampling runs repeatedly on the sampling scope until stopped`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        var diskReads = 0
        viewModel.diskStateReader = { diskReads++; DiskState(pct = 42) }
        viewModel.samplingIntervalMs = 5_000L
        viewModel.samplingScope = this
        viewModel.readingDispatcher = testDispatcher

        viewModel.startSampling()
        // Anchor runs synchronously; then passes at 5s and 10s.
        advanceTimeBy(12_001L)
        assertTrue("expected periodic sampling passes", diskReads >= 3)

        viewModel.stopSampling()
        val readsAtStop = diskReads
        advanceTimeBy(20_000L)
        assertEquals("sampling must stop with the loop", readsAtStop, diskReads)
    }

    @Test
    fun `first sampling pass refreshes state without hidden-interval rates`() = runTest(testDispatcher.scheduler) {
        val viewModel = DeviceInfoViewModel(context)
        viewModel.samplingIntervalMs = 5_000L
        viewModel.samplingScope = this
        viewModel.readingDispatcher = testDispatcher

        viewModel.startSampling()
        advanceTimeBy(1_001L)

        // First pass after (re)start re-anchors rate sources: traffic is
        // reported as not-ready, and the pass itself has refreshed the row.
        assertFalse(viewModel.uiState.value.network.throughputReady)
        assertTrue(viewModel.uiState.value.time.currentTime.isNotEmpty())

        viewModel.stopSampling()
        advanceUntilIdle()
    }
}
