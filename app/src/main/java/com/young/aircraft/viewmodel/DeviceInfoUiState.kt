package com.young.aircraft.viewmodel

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
    val coreUsages: List<CoreUsage> = emptyList(),
    val temperature: Float? = null
)

data class CoreUsage(
    val index: Int,
    val pct: Int,
    val freqMhz: Int? = null,
    val isOnline: Boolean = true
)

data class MemoryState(
    val pct: Int = 0,
    val usedGB: Double = 0.0,
    val availGB: Double = 0.0,
    val totalGB: Double = 0.0,
    val buffersGB: Double = 0.0,
    val cachedGB: Double = 0.0
)

data class DiskState(
    val pct: Int = 0,
    val usedGB: Double = 0.0,
    val availGB: Double = 0.0,
    val totalGB: Double = 0.0
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
