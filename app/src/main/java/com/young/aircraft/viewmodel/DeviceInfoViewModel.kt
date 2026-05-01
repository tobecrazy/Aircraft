package com.young.aircraft.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceInfoViewModel(
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    private val numCores = Runtime.getRuntime().availableProcessors()
    private var prevTotalLine: LongArray? = null
    private var prevCoreLines: List<LongArray>? = null
    private var maxFreqs: LongArray? = null

    private var prevRxBytes = 0L
    private var prevTxBytes = 0L
    private var prevTrafficTimestamp = 0L

    val coreCount: Int
        get() {
            val snap = readProcStatSnapshot()
            return snap?.perCore?.size ?: numCores
        }

    fun initStaticInfo() {
        val dm = appContext.resources.displayMetrics
        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        _uiState.value = _uiState.value.copy(
            staticInfo = DeviceStaticInfo(
                deviceModel = appContext.getString(
                    R.string.device_info_fmt_device_model,
                    Build.MANUFACTURER.uppercase(Locale.getDefault()),
                    Build.MODEL
                ),
                androidVersion = appContext.getString(
                    R.string.device_info_fmt_android,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                ),
                cpuInfo = getCpuInfo(),
                screenResolution = appContext.getString(
                    R.string.device_info_fmt_resolution,
                    dm.widthPixels,
                    dm.heightPixels,
                    dm.densityDpi
                ),
                bootTime = dateFormat.format(Date(bootTimeMillis)),
                appVersion = appContext.getString(
                    R.string.device_info_fmt_version,
                    BuildConfig.VERSION_NAME
                )
            )
        )
    }

    fun initCpuSnapshot() {
        val snap = readProcStatSnapshot()
        if (snap != null) {
            prevTotalLine = snap.total
            prevCoreLines = snap.perCore
            return
        }
        maxFreqs = readMaxCpuFrequencies()
    }

    fun initTrafficSnapshot() {
        prevRxBytes = TrafficStats.getTotalRxBytes()
        prevTxBytes = TrafficStats.getTotalTxBytes()
        prevTrafficTimestamp = SystemClock.elapsedRealtime()
    }

    fun refreshDynamicInfo() {
        val cpuState = refreshCpuUsage()
        val memoryState = refreshMemoryInfo()
        val diskState = refreshDiskInfo()
        val networkState = refreshNetworkInfo()
        val timeState = refreshTimeInfo()

        _uiState.value = _uiState.value.copy(
            cpu = cpuState,
            memory = memoryState,
            disk = diskState,
            network = networkState,
            time = timeState
        )
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        _uiState.value = _uiState.value.copy(
            battery = BatteryState(pct = pct, isCharging = charging)
        )
    }

    // ── CPU ───────────────────────────────────────────────────────────────

    private fun getCpuInfo(): String {
        return try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val hardware = BufferedReader(FileReader("/proc/cpuinfo")).useLines { lines ->
                lines.firstOrNull { it.startsWith("Hardware") }
                    ?.substringAfter(":")?.trim()
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            appContext.getString(R.string.device_info_fmt_cpu_detail, hardware ?: Build.HARDWARE, cpuCores, abi)
        } catch (_: Exception) {
            appContext.getString(R.string.device_info_fmt_cpu_detail, Build.HARDWARE, Runtime.getRuntime().availableProcessors(), "")
        }
    }

    private fun refreshCpuUsage(): CpuState {
        val statState = refreshCpuFromProcStat()
        if (statState != null) return statState

        if (maxFreqs == null) {
            maxFreqs = readMaxCpuFrequencies()
        }
        return refreshCpuFromFreq()
    }

    private fun refreshCpuFromProcStat(): CpuState? {
        val snap = readProcStatSnapshot() ?: return null
        val prevTotal = prevTotalLine
        val prevCores = prevCoreLines
        val previousCpu = _uiState.value.cpu

        val overallPct = if (prevTotal != null) {
            DeviceResourceCalculators.calcCpuUsagePercent(prevTotal, snap.total) ?: previousCpu.overallPct
        } else previousCpu.overallPct

        val coreUsages = if (prevCores != null) {
            snap.perCore.mapIndexed { i, core ->
                val previousCore = previousCpu.coreUsages.getOrNull(i)
                val pct = if (i < prevCores.size) {
                    DeviceResourceCalculators.calcCpuUsagePercent(prevCores[i], core) ?: previousCore?.pct ?: 0
                } else {
                    previousCore?.pct ?: 0
                }
                CoreUsage(
                    index = i,
                    pct = pct,
                    freqMhz = readCurrentCpuFrequencyMhz(i),
                    isOnline = isCpuCoreOnline(i)
                )
            }
        } else {
            snap.perCore.mapIndexed { i, _ ->
                CoreUsage(
                    index = i,
                    pct = previousCpu.coreUsages.getOrNull(i)?.pct ?: 0,
                    freqMhz = readCurrentCpuFrequencyMhz(i),
                    isOnline = isCpuCoreOnline(i)
                )
            }
        }

        prevTotalLine = snap.total
        prevCoreLines = snap.perCore

        return CpuState(
            overallPct = overallPct,
            coreUsages = coreUsages,
            temperature = readCpuTemperature()
        )
    }

    private fun refreshCpuFromFreq(): CpuState {
        val maxes = maxFreqs ?: return _uiState.value.cpu
        val coreUsages = mutableListOf<CoreUsage>()
        var totalPct = 0
        var activeCount = 0

        for (i in 0 until numCores) {
            val curFreq = readCurrentCpuFrequency(i)
            val maxFreq = maxes[i]
            val pct = if (curFreq != null && maxFreq > 0) {
                (curFreq * 100 / maxFreq).toInt().coerceIn(0, 100)
            } else {
                if (isCpuCoreOnline(i)) -1 else 0
            }

            if (pct >= 0 && isCpuCoreOnline(i)) {
                coreUsages.add(CoreUsage(
                    index = i,
                    pct = pct,
                    freqMhz = curFreq?.let { (it / 1000).toInt() }
                ))
                totalPct += pct
                activeCount++
            } else {
                coreUsages.add(CoreUsage(index = i, pct = 0, isOnline = false))
            }
        }

        val overallPct = if (activeCount > 0) totalPct / activeCount else 0
        return CpuState(
            overallPct = overallPct,
            coreUsages = coreUsages,
            temperature = readCpuTemperature()
        )
    }

    private fun readMaxCpuFrequencies(): LongArray {
        val maxes = LongArray(numCores)
        for (i in 0 until numCores) {
            maxes[i] = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                ?: readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")
                ?: 0L
        }
        return maxes
    }

    private fun readCurrentCpuFrequency(coreIndex: Int): Long? {
        return readSysFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq")
            ?: readSysFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/cpuinfo_cur_freq")
    }

    private fun readCurrentCpuFrequencyMhz(coreIndex: Int): Int? {
        return readCurrentCpuFrequency(coreIndex)?.let { (it / 1000).toInt() }
    }

    private fun isCpuCoreOnline(coreIndex: Int): Boolean {
        return readSysFile("/sys/devices/system/cpu/cpu$coreIndex/online") != 0L
    }

    private fun readCpuTemperature(): Float? {
        return try {
            val dir = File("/sys/class/thermal/")
            val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
                ?: return null
            for (zone in zones) {
                val type = File(zone, "type").readText().trim().lowercase()
                if (type.contains("cpu") || type.contains("soc")) {
                    val tempRaw = File(zone, "temp").readText().trim().toLongOrNull() ?: continue
                    return tempRaw / 1000f
                }
            }
            val tempRaw = File(zones[0], "temp").readText().trim().toLongOrNull() ?: return null
            tempRaw / 1000f
        } catch (_: Exception) {
            null
        }
    }

    private data class CpuSnapshot(val total: LongArray, val perCore: List<LongArray>)

    private fun readProcStatSnapshot(): CpuSnapshot? {
        return try {
            val lines = BufferedReader(FileReader("/proc/stat")).use { reader ->
                val result = mutableListOf<String>()
                var line = reader.readLine()
                while (line != null && line.startsWith("cpu")) {
                    result.add(line)
                    line = reader.readLine()
                }
                result
            }
            if (lines.isEmpty()) return null
            val total = DeviceResourceCalculators.parseCpuStatLine(lines[0])
            if (total == null || total.sum() == 0L) return null
            val cores = lines.drop(1).mapNotNull { DeviceResourceCalculators.parseCpuStatLine(it) }
            CpuSnapshot(total, cores)
        } catch (_: Exception) {
            null
        }
    }

    private fun readSysFile(path: String): Long? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim()?.toLongOrNull() }
        } catch (_: Exception) {
            null
        }
    }

    // ── Memory ────────────────────────────────────────────────────────────

    private fun refreshMemoryInfo(): MemoryState {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val memInfo = readProcMemInfo()
        val totalBytes = memInfo["MemTotal"]?.times(BYTES_PER_KB) ?: mi.totalMem
        val availableBytes = memInfo["MemAvailable"]?.times(BYTES_PER_KB) ?: mi.availMem
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val totalGB = totalBytes / BYTES_PER_GB
        val availGB = availableBytes / BYTES_PER_GB
        val usedGB = usedBytes / BYTES_PER_GB
        val pct = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0

        val buffersGB = memInfo["Buffers"]?.let { it / KB_PER_GB } ?: 0.0
        val cachedKB = (memInfo["Cached"] ?: 0L) + (memInfo["SReclaimable"] ?: 0L) - (memInfo["Shmem"] ?: 0L)
        val cachedGB = cachedKB.coerceAtLeast(0L) / KB_PER_GB

        return MemoryState(
            pct = pct,
            usedGB = usedGB,
            availGB = availGB,
            totalGB = totalGB,
            buffersGB = buffersGB,
            cachedGB = cachedGB
        )
    }

    companion object {
        private const val BYTES_PER_KB = 1024L
        private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
        private const val KB_PER_GB = 1024.0 * 1024.0
    }

    private fun readProcMemInfo(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val valueKB = parts[1].trim().split("\\s+".toRegex())[0].toLongOrNull()
                        if (valueKB != null) result[key] = valueKB
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) { }
        return result
    }

    // ── Disk ──────────────────────────────────────────────────────────────

    private fun refreshDiskInfo(): DiskState {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val availBytes = stat.blockSizeLong * stat.availableBlocksLong
        val usedBytes = totalBytes - availBytes
        val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val availGB = availBytes / (1024.0 * 1024.0 * 1024.0)
        val pct = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0

        return DiskState(pct = pct, usedGB = usedGB, availGB = availGB, totalGB = totalGB)
    }

    // ── Network ───────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun refreshNetworkInfo(): NetworkState {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
            ?: return NetworkState(type = NetworkType.OFFLINE)
        val caps = cm.getNetworkCapabilities(network)
            ?: return NetworkState(type = NetworkType.OFFLINE)

        val type: NetworkType
        val detail: String

        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                type = NetworkType.WIFI
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                val ssid = info?.ssid?.removeSurrounding("\"") ?: ""
                val linkSpeed = info?.linkSpeed ?: 0
                val rssi = info?.rssi ?: 0
                val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
                detail = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                    appContext.getString(R.string.device_info_net_wifi_detail_ext, ssid, linkSpeed, rssi, signalLevel)
                } else {
                    appContext.getString(R.string.device_info_net_connected_speed, linkSpeed)
                }
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                type = NetworkType.CELLULAR
                val downstream = caps.linkDownstreamBandwidthKbps
                detail = if (downstream > 0)
                    appContext.getString(R.string.device_info_net_cellular_speed, downstream / 1000)
                else
                    appContext.getString(R.string.device_info_net_mobile_data)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                type = NetworkType.ETHERNET
                detail = appContext.getString(R.string.device_info_net_wired)
            }
            else -> {
                type = NetworkType.OTHER
                detail = appContext.getString(R.string.device_info_net_connected)
            }
        }

        val nowMs = SystemClock.elapsedRealtime()
        val currRx = TrafficStats.getTotalRxBytes()
        val currTx = TrafficStats.getTotalTxBytes()
        val dtSec = (nowMs - prevTrafficTimestamp) / 1000.0

        var rxBps = 0L
        var txBps = 0L
        var throughputReady = false

        if (dtSec > 0 && prevTrafficTimestamp > 0
            && currRx != TrafficStats.UNSUPPORTED.toLong()
            && currTx != TrafficStats.UNSUPPORTED.toLong()
        ) {
            rxBps = ((currRx - prevRxBytes) / dtSec).toLong().coerceAtLeast(0)
            txBps = ((currTx - prevTxBytes) / dtSec).toLong().coerceAtLeast(0)
            throughputReady = true
        }
        prevRxBytes = currRx
        prevTxBytes = currTx
        prevTrafficTimestamp = nowMs

        return NetworkState(
            type = type,
            detail = detail,
            rxBytesPerSec = rxBps,
            txBytesPerSec = txBps,
            ipAddress = getLocalIpAddress(),
            throughputReady = throughputReady
        )
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }

    // ── Time ──────────────────────────────────────────────────────────────

    private fun refreshTimeInfo(): TimeState {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return TimeState(
            currentTime = dateFormat.format(Date()),
            uptime = getUptime()
        )
    }

    private fun getUptime(): String {
        val totalSec = SystemClock.elapsedRealtime() / 1000L
        val ss = totalSec % 60
        val mm = (totalSec / 60) % 60
        val hh = (totalSec / 3600) % 24
        val totalDays = totalSec / 86400

        return when {
            totalSec < 3600 -> {
                String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
            }
            totalDays < 1 -> {
                val h = totalSec / 3600
                String.format(Locale.getDefault(), "%02d:%02d:%02d", h, mm, ss)
            }
            totalDays < 30 -> {
                appContext.getString(R.string.device_info_fmt_uptime_day_hms, totalDays, hh, mm, ss)
            }
            totalDays < 365 -> {
                val months = totalDays / 30
                val days = totalDays % 30
                appContext.getString(R.string.device_info_fmt_uptime_month, months, days, hh, mm, ss)
            }
            else -> {
                val years = totalDays / 365
                val remainDays = totalDays % 365
                val months = remainDays / 30
                val days = remainDays % 30
                appContext.getString(R.string.device_info_fmt_uptime_year, years, months, days, hh, mm, ss)
            }
        }
    }

    // ── Formatting helpers (exposed for Activity rendering) ───────────────

    fun formatBytes(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1_000.0)
            else -> String.format(Locale.getDefault(), "%d B/s", bytesPerSec)
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceInfoViewModel(appContext) as T
        }
    }
}

internal object DeviceResourceCalculators {
    private val whitespace = "\\s+".toRegex()

    fun parseCpuStatLine(line: String): LongArray? {
        val parts = line.trim().split(whitespace)
        if (parts.size < 5 || !parts[0].startsWith("cpu")) return null
        return LongArray(minOf(parts.size - 1, CPU_STAT_FIELD_COUNT)) { i ->
            parts.getOrNull(i + 1)?.toLongOrNull() ?: 0L
        }
    }

    fun calcCpuUsagePercent(prev: LongArray, curr: LongArray): Int? {
        val prevIdle = idleTicks(prev)
        val currIdle = idleTicks(curr)
        val prevTotal = totalTicks(prev)
        val currTotal = totalTicks(curr)
        val diffTotal = currTotal - prevTotal
        val diffIdle = currIdle - prevIdle

        if (diffTotal <= 0 || diffIdle < 0) return null
        return ((diffTotal - diffIdle).coerceAtLeast(0L) * 100 / diffTotal)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun idleTicks(values: LongArray): Long {
        return values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
    }

    private fun totalTicks(values: LongArray): Long {
        var total = 0L
        for (i in 0 until minOf(values.size, CPU_STAT_FIELD_COUNT)) {
            total += values[i]
        }
        return total
    }

    private const val CPU_STAT_FIELD_COUNT = 8
}
