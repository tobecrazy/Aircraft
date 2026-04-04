package com.young.aircraft.gui

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityDeviceInfoBinding
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceInfoBinding

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDynamicInfo()
            handler.postDelayed(this, 1000L)
        }
    }

    private var coreRows: List<CoreRow>? = null

    // Battery state
    private var batteryPct = 0
    private var batteryCharging = false

    // Network throughput tracking
    private var prevRxBytes = 0L
    private var prevTxBytes = 0L
    private var prevTrafficTimestamp = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryPct = if (scale > 0) (level * 100 / scale) else 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
            updateBatteryUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbMemory.progressDrawable = it }
        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbDisk.progressDrawable = it }
        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbCpuUsage.progressDrawable = it }

        populateStaticInfo()
        initCpuSnapshot()
        initTrafficSnapshot()
        refreshDynamicInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
        unregisterReceiver(batteryReceiver)
    }

    // ── Static info ────────────────────────────────────────────────────

    private fun populateStaticInfo() {
        binding.tvDeviceModel.text =
            getString(R.string.device_info_fmt_device_model, Build.MANUFACTURER.uppercase(Locale.getDefault()), Build.MODEL)

        binding.tvAndroidVersion.text =
            getString(R.string.device_info_fmt_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)

        binding.tvCpu.text = getCpuInfo()

        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        binding.tvBootTime.text = dateFormat.format(Date(bootTimeMillis))

        val dm = resources.displayMetrics
        binding.tvScreenResolution.text =
            getString(R.string.device_info_fmt_resolution, dm.widthPixels, dm.heightPixels, dm.densityDpi)

        binding.tvAppVersion.text =
            getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
    }

    // ── Dynamic info ───────────────────────────────────────────────────

    private fun refreshDynamicInfo() {
        refreshMemoryInfo()
        refreshDiskInfo()
        refreshNetworkInfo()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        binding.tvCurrentTime.text = dateFormat.format(Date())
        binding.tvUptime.text = getUptime()

        refreshCpuUsage()
    }

    // ── Battery ────────────────────────────────────────────────────────

    private fun updateBatteryUI() {
        binding.tvBattery.text = getString(R.string.device_info_fmt_pct, batteryPct)
        binding.tvBattery.setTextColor(
            when {
                batteryPct <= 15 -> 0xFFFF4444.toInt()
                batteryPct <= 30 -> 0xFFFFFF00.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        )
        binding.tvBatteryStatus.text = if (batteryCharging)
            getString(R.string.device_info_battery_charging)
        else
            getString(R.string.device_info_battery_discharging)
    }

    // ── CPU info (static) ──────────────────────────────────────────────

    private fun getCpuInfo(): String {
        return try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val hardware = BufferedReader(FileReader("/proc/cpuinfo")).useLines { lines ->
                lines.firstOrNull { it.startsWith("Hardware") }
                    ?.substringAfter(":")?.trim()
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            getString(R.string.device_info_fmt_cpu_detail, hardware ?: Build.HARDWARE, cpuCores, abi)
        } catch (_: Exception) {
            getString(R.string.device_info_fmt_cpu_detail, Build.HARDWARE, Runtime.getRuntime().availableProcessors(), "")
        }
    }

    // ── CPU usage (dynamic) ────────────────────────────────────────────

    private var useProcStat = false
    private val numCores = Runtime.getRuntime().availableProcessors()
    private var prevTotalLine: LongArray? = null
    private var prevCoreLines: List<LongArray>? = null
    private var maxFreqs: LongArray? = null

    private fun parseCpuLine(line: String): LongArray {
        val parts = line.trim().split("\\s+".toRegex())
        return LongArray(minOf(parts.size - 1, 8)) { i ->
            parts.getOrNull(i + 1)?.toLongOrNull() ?: 0L
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
            val total = parseCpuLine(lines[0])
            if (total.sum() == 0L) return null
            val cores = lines.drop(1).map { parseCpuLine(it) }
            CpuSnapshot(total, cores)
        } catch (_: Exception) {
            null
        }
    }

    private fun calcUsagePercent(prev: LongArray, curr: LongArray): Int {
        val prevIdle = prev.getOrElse(3) { 0L } + prev.getOrElse(4) { 0L }
        val currIdle = curr.getOrElse(3) { 0L } + curr.getOrElse(4) { 0L }
        val prevTotal = prev.sum()
        val currTotal = curr.sum()
        val diffTotal = currTotal - prevTotal
        val diffIdle = currIdle - prevIdle
        return if (diffTotal > 0) ((diffTotal - diffIdle) * 100 / diffTotal).toInt().coerceIn(0, 100) else 0
    }

    private fun readSysFile(path: String): Long? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim()?.toLongOrNull() }
        } catch (_: Exception) {
            null
        }
    }

    private fun initCpuSnapshot() {
        val snap = readProcStatSnapshot()
        if (snap != null) {
            useProcStat = true
            prevTotalLine = snap.total
            prevCoreLines = snap.perCore
            buildCoreRows(snap.perCore.size)
            return
        }
        useProcStat = false
        val maxes = LongArray(numCores)
        for (i in 0 until numCores) {
            maxes[i] = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                ?: readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")
                ?: 0L
        }
        maxFreqs = maxes
        buildCoreRows(numCores)
    }

    private fun refreshCpuUsage() {
        if (useProcStat) refreshCpuFromProcStat() else refreshCpuFromFreq()
        refreshCpuTemp()
    }

    private fun refreshCpuTemp() {
        val temp = readCpuTemperature()
        binding.tvCpuTemp.text = if (temp != null) {
            String.format(Locale.getDefault(), getString(R.string.device_info_fmt_cpu_temp), temp)
        } else {
            getString(R.string.device_info_cpu_temp_na)
        }
        binding.tvCpuTemp.setTextColor(
            when {
                temp == null -> 0x55FFFFFF
                temp >= 70f -> 0xFFFF4444.toInt()
                temp >= 50f -> 0xFFFFFF00.toInt()
                else -> 0xFF00FF88.toInt()
            }
        )
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

    private fun refreshCpuFromProcStat() {
        val snap = readProcStatSnapshot() ?: return
        val prevTotal = prevTotalLine
        val prevCores = prevCoreLines

        if (prevTotal != null) {
            val pct = calcUsagePercent(prevTotal, snap.total)
            binding.tvCpuUsagePct.text = getString(R.string.device_info_fmt_pct, pct)
            binding.pbCpuUsage.progress = pct
            updateProgressBarColor(binding.pbCpuUsage, pct)
        }

        if (prevCores != null) {
            val rows = coreRows ?: return
            for (i in rows.indices) {
                if (i < snap.perCore.size && i < prevCores.size) {
                    val pct = calcUsagePercent(prevCores[i], snap.perCore[i])
                    rows[i].pctText.text = getString(R.string.device_info_fmt_core_pct, pct)
                    rows[i].bar.progress = pct
                    updateProgressBarColor(rows[i].bar, pct)
                }
            }
        }
        prevTotalLine = snap.total
        prevCoreLines = snap.perCore
    }

    private fun refreshCpuFromFreq() {
        val maxes = maxFreqs ?: return
        val rows = coreRows ?: return
        var totalPct = 0
        var activeCount = 0

        for (i in 0 until numCores) {
            val curFreq = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                ?: readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_cur_freq")
            val maxFreq = maxes[i]
            val pct = if (curFreq != null && maxFreq > 0) {
                (curFreq * 100 / maxFreq).toInt().coerceIn(0, 100)
            } else {
                val online = readSysFile("/sys/devices/system/cpu/cpu$i/online")
                if (online == 0L) 0 else -1
            }

            if (i < rows.size) {
                if (pct >= 0) {
                    rows[i].pctText.text = if (curFreq != null) {
                        getString(R.string.device_info_fmt_core_pct_freq, pct, (curFreq / 1000).toInt())
                    } else {
                        getString(R.string.device_info_fmt_core_pct, pct)
                    }
                    rows[i].bar.progress = pct
                    updateProgressBarColor(rows[i].bar, pct)
                    totalPct += pct
                    activeCount++
                } else {
                    rows[i].pctText.text = getString(R.string.device_info_cpu_core_off)
                    rows[i].bar.progress = 0
                    updateProgressBarColor(rows[i].bar, 0)
                }
            }
        }
        val overallPct = if (activeCount > 0) totalPct / activeCount else 0
        binding.tvCpuUsagePct.text = getString(R.string.device_info_fmt_pct, overallPct)
        binding.pbCpuUsage.progress = overallPct
        updateProgressBarColor(binding.pbCpuUsage, overallPct)
    }

    private fun updateProgressBarColor(bar: ProgressBar, pct: Int) {
        val color = when {
            pct >= 80 -> 0xFFFF4444.toInt()
            pct >= 50 -> 0xFFFFFF00.toInt()
            else -> 0xFF00FF88.toInt()
        }
        bar.progressDrawable?.setTint(color)
    }

    private data class CoreRow(val label: TextView, val bar: ProgressBar, val pctText: TextView)

    private fun buildCoreRows(count: Int) {
        binding.llCpuCores.removeAllViews()
        val rows = mutableListOf<CoreRow>()
        val dp = resources.displayMetrics
        val barH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, dp).toInt()
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, dp).toInt()
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, dp).toInt()

        for (i in 0 until count) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = gap }
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = String.format(Locale.getDefault(), "%d", i)
                setTextColor(0x66FFFFFF)
                textSize = 9f
                typeface = Typeface.MONOSPACE
            }

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, barH, 1f).apply {
                    marginStart = pad
                    marginEnd = pad
                }
                max = 100
                ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { progressDrawable = it }
            }

            val pctText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = getString(R.string.device_info_fmt_core_init)
                setTextColor(0xFF00FF88.toInt())
                textSize = 9f
                typeface = Typeface.MONOSPACE
            }

            row.addView(label)
            row.addView(bar)
            row.addView(pctText)
            binding.llCpuCores.addView(row)
            rows.add(CoreRow(label, bar, pctText))
        }
        coreRows = rows
    }

    // ── Memory ─────────────────────────────────────────────────────────

    private fun refreshMemoryInfo() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalGB = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGB = mi.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedGB = totalGB - availGB
        val pct = if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt().coerceIn(0, 100) else 0

        binding.tvMemoryPct.text = getString(R.string.device_info_fmt_pct, pct)
        binding.tvMemoryPct.setTextColor(pctColor(pct))
        binding.pbMemory.progress = pct
        updateProgressBarColor(binding.pbMemory, pct)

        val memInfo = readProcMemInfo()
        val buffersGB = memInfo["Buffers"]?.let { it / (1024.0 * 1024.0) } ?: 0.0
        val cachedGB = memInfo["Cached"]?.let { it / (1024.0 * 1024.0) } ?: 0.0

        binding.tvMemory.text = String.format(
            Locale.getDefault(),
            getString(R.string.device_info_fmt_memory_detail),
            usedGB, availGB, totalGB, buffersGB, cachedGB
        )
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

    // ── Disk ───────────────────────────────────────────────────────────

    private fun refreshDiskInfo() {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val availBytes = stat.blockSizeLong * stat.availableBlocksLong
        val usedBytes = totalBytes - availBytes
        val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val availGB = availBytes / (1024.0 * 1024.0 * 1024.0)
        val pct = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0

        binding.tvDiskPct.text = getString(R.string.device_info_fmt_pct, pct)
        binding.tvDiskPct.setTextColor(pctColor(pct))
        binding.pbDisk.progress = pct
        updateProgressBarColor(binding.pbDisk, pct)
        binding.tvDisk.text = String.format(
            Locale.getDefault(),
            getString(R.string.device_info_fmt_disk_detail),
            usedGB, availGB, totalGB
        )
    }

    private fun pctColor(pct: Int): Int = when {
        pct >= 80 -> 0xFFFF4444.toInt()
        pct >= 50 -> 0xFFFFFF00.toInt()
        else -> 0xFF00FF88.toInt()
    }

    // ── Network ────────────────────────────────────────────────────────

    private fun initTrafficSnapshot() {
        prevRxBytes = TrafficStats.getTotalRxBytes()
        prevTxBytes = TrafficStats.getTotalTxBytes()
        prevTrafficTimestamp = SystemClock.elapsedRealtime()
    }

    @Suppress("DEPRECATION")
    private fun refreshNetworkInfo() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        if (network == null) {
            binding.tvNetwork.text = getString(R.string.device_info_net_offline)
            binding.tvNetwork.setTextColor(0xFFFF4444.toInt())
            binding.tvNetworkDetail.text = getString(R.string.device_info_net_no_connection)
            binding.tvNetworkThroughput.text = ""
            binding.tvNetworkExtra.text = ""
            return
        }
        val caps = cm.getNetworkCapabilities(network)
        if (caps == null) {
            binding.tvNetwork.text = getString(R.string.device_info_net_offline)
            binding.tvNetwork.setTextColor(0xFFFF4444.toInt())
            binding.tvNetworkDetail.text = getString(R.string.device_info_net_no_connection)
            binding.tvNetworkThroughput.text = ""
            binding.tvNetworkExtra.text = ""
            return
        }

        binding.tvNetwork.setTextColor(0xFFFFFFFF.toInt())
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_wifi)
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                val ssid = info?.ssid?.removeSurrounding("\"") ?: ""
                val linkSpeed = info?.linkSpeed ?: 0
                val rssi = info?.rssi ?: 0
                val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
                binding.tvNetworkDetail.text = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                    getString(R.string.device_info_net_wifi_detail_ext, ssid, linkSpeed, rssi, signalLevel)
                } else {
                    getString(R.string.device_info_net_connected_speed, linkSpeed)
                }
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_cellular)
                val downstream = caps.linkDownstreamBandwidthKbps
                binding.tvNetworkDetail.text = if (downstream > 0)
                    getString(R.string.device_info_net_cellular_speed, downstream / 1000)
                else
                    getString(R.string.device_info_net_mobile_data)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_ethernet)
                binding.tvNetworkDetail.text = getString(R.string.device_info_net_wired)
            }
            else -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_online)
                binding.tvNetworkDetail.text = getString(R.string.device_info_net_connected)
            }
        }

        // Throughput calculation
        val nowMs = SystemClock.elapsedRealtime()
        val currRx = TrafficStats.getTotalRxBytes()
        val currTx = TrafficStats.getTotalTxBytes()
        val dtSec = (nowMs - prevTrafficTimestamp) / 1000.0

        if (dtSec > 0 && prevTrafficTimestamp > 0
            && currRx != TrafficStats.UNSUPPORTED.toLong()
            && currTx != TrafficStats.UNSUPPORTED.toLong()
        ) {
            val rxBps = ((currRx - prevRxBytes) / dtSec).toLong().coerceAtLeast(0)
            val txBps = ((currTx - prevTxBytes) / dtSec).toLong().coerceAtLeast(0)
            binding.tvNetworkThroughput.text = getString(
                R.string.device_info_net_throughput,
                formatBytes(rxBps), formatBytes(txBps)
            )
        } else {
            binding.tvNetworkThroughput.text = getString(R.string.device_info_net_throughput_init)
        }
        prevRxBytes = currRx
        prevTxBytes = currTx
        prevTrafficTimestamp = nowMs

        // IP Address
        binding.tvNetworkExtra.text = getString(R.string.device_info_net_ip, getLocalIpAddress())
    }

    private fun formatBytes(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1_000.0)
            else -> String.format(Locale.getDefault(), "%d B/s", bytesPerSec)
        }
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

    // ── Uptime ─────────────────────────────────────────────────────────

    private fun getUptime(): String {
        val totalSec = SystemClock.elapsedRealtime() / 1000L
        val ss = totalSec % 60
        val mm = (totalSec / 60) % 60
        val hh = (totalSec / 3600) % 24
        val totalDays = totalSec / 86400

        return when {
            // < 1 hour: mm:ss
            totalSec < 3600 -> {
                String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
            }
            // 1h to 24h: hh:mm:ss
            totalDays < 1 -> {
                val h = totalSec / 3600
                String.format(Locale.getDefault(), "%02d:%02d:%02d", h, mm, ss)
            }
            // 1d to 30d: X day hh:mm:ss
            totalDays < 30 -> {
                getString(R.string.device_info_fmt_uptime_day_hms, totalDays, hh, mm, ss)
            }
            // 30d to 365d: X Month Y day hh:mm:ss
            totalDays < 365 -> {
                val months = totalDays / 30
                val days = totalDays % 30
                getString(R.string.device_info_fmt_uptime_month, months, days, hh, mm, ss)
            }
            // 365d+: Xy Xm Xd hh:mm:ss
            else -> {
                val years = totalDays / 365
                val remainDays = totalDays % 365
                val months = remainDays / 30
                val days = remainDays % 30
                getString(R.string.device_info_fmt_uptime_year, years, months, days, hh, mm, ss)
            }
        }
    }
}
