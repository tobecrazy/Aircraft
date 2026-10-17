package com.young.developtools.viewmodel

data class DeviceInfoUiState(
    val cpu: CpuState = CpuState(),
    val memory: MemoryState = MemoryState(),
    val disk: DiskState = DiskState(),
    val network: NetworkState = NetworkState(),
    val battery: BatteryState = BatteryState(),
    val time: TimeState = TimeState(),
    val staticInfo: DeviceStaticInfo = DeviceStaticInfo()
)

data class CpuState(
    val overallPct: Int = 0,
    /**
     * True only when the overall percentage is a real /proc/stat measurement
     * (or a fabricated preview). Defaults to false so an unknown value is
     * never rendered as a measured 0%.
     */
    val overallPctAvailable: Boolean = false,
    val coreUsages: List<CoreUsage> = emptyList(),
    val temperature: Float? = null,
    /**
     * True when the usage numbers come from the frequency-based fallback
     * (current frequency / max frequency) instead of measured busy time.
     * Frequency does not equal utilization, so the UI must label these as estimates.
     */
    val isEstimate: Boolean = false
)

data class CoreUsage(
    val index: Int,
    val pct: Int,
    val freqMhz: Int? = null,
    val isOnline: Boolean = true,
    /** False when this core's utilization could not be read; defaults to false
     *  so an unknown core value is never rendered as a measured 0%. */
    val pctAvailable: Boolean = false
)

data class MemoryState(
    val pct: Int = 0,
    val usedGB: Double = 0.0,
    val availGB: Double = 0.0,
    val totalGB: Double = 0.0,
    val buffersGB: Double = 0.0,
    val cachedGB: Double = 0.0,
    /** False until a real memory read succeeded; a false value must render as N/A, not 0%. */
    val pctAvailable: Boolean = false
)

data class DiskState(
    val pct: Int = 0,
    val usedGB: Double = 0.0,
    val availGB: Double = 0.0,
    val totalGB: Double = 0.0,
    /** False until a real storage read succeeded; a false value must render as N/A, not 0%. */
    val pctAvailable: Boolean = false
)

data class NetworkState(
    val type: NetworkType = NetworkType.OFFLINE,
    val detail: String = "",
    val rxBytesPerSec: Long = 0,
    val txBytesPerSec: Long = 0,
    val ipAddress: String = "N/A",
    val throughputReady: Boolean = false
)

enum class NetworkType { OFFLINE, WIFI, CELLULAR, ETHERNET, OTHER }

data class BatteryState(
    val pct: Int = 0,
    val isCharging: Boolean = false
)

data class TimeState(
    val currentTime: String = "",
    val uptime: String = ""
)

data class DeviceStaticInfo(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val cpuInfo: String = "",
    val screenResolution: String = "",
    val bootTime: String = "",
    val appVersion: String = ""
)
