package com.young.aircraft.gui

import android.app.ActivityManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceInfoActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDynamicInfo()
            handler.postDelayed(this, 1000L)
        }
    }

    private lateinit var tvMemory: TextView
    private lateinit var tvMemoryPct: TextView
    private lateinit var pbMemory: ProgressBar
    private lateinit var tvDisk: TextView
    private lateinit var tvDiskPct: TextView
    private lateinit var pbDisk: ProgressBar
    private lateinit var tvNetwork: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvCpuUsagePct: TextView
    private lateinit var pbCpuUsage: ProgressBar
    private lateinit var llCpuCores: LinearLayout

    // Previous /proc/stat snapshot for delta calculation
    private var prevTotalCpu: LongArray? = null
    private var prevPerCoreCpu: List<LongArray>? = null

    // Per-core UI rows (reused across refreshes)
    private var coreRows: List<CoreRow>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        tvMemory = findViewById(R.id.tv_memory)
        tvMemoryPct = findViewById(R.id.tv_memory_pct)
        pbMemory = findViewById(R.id.pb_memory)
        pbMemory.progressDrawable = resources.getDrawable(R.drawable.cpu_progress_bar, null).mutate()
        tvDisk = findViewById(R.id.tv_disk)
        tvDiskPct = findViewById(R.id.tv_disk_pct)
        pbDisk = findViewById(R.id.pb_disk)
        pbDisk.progressDrawable = resources.getDrawable(R.drawable.cpu_progress_bar, null).mutate()
        tvNetwork = findViewById(R.id.tv_network)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvUptime = findViewById(R.id.tv_uptime)
        tvCpuUsagePct = findViewById(R.id.tv_cpu_usage_pct)
        pbCpuUsage = findViewById(R.id.pb_cpu_usage)
        llCpuCores = findViewById(R.id.ll_cpu_cores)

        populateStaticInfo()
        initCpuSnapshot()
        refreshDynamicInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun populateStaticInfo() {
        findViewById<TextView>(R.id.tv_device_model).text =
            "${Build.MANUFACTURER} ${Build.MODEL}"

        findViewById<TextView>(R.id.tv_android_version).text =
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        findViewById<TextView>(R.id.tv_cpu).text = getCpuInfo()

        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        findViewById<TextView>(R.id.tv_boot_time).text = dateFormat.format(Date(bootTimeMillis))

        val dm = resources.displayMetrics
        findViewById<TextView>(R.id.tv_screen_resolution).text =
            "${dm.widthPixels} x ${dm.heightPixels} (${dm.densityDpi} dpi)"

        findViewById<TextView>(R.id.tv_app_version).text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun refreshDynamicInfo() {
        refreshMemoryInfo()
        refreshDiskInfo()
        tvNetwork.text = getNetworkInfo()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        tvCurrentTime.text = dateFormat.format(Date())
        tvUptime.text = getUptime()

        refreshCpuUsage()
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
            "${hardware ?: Build.HARDWARE} | $cpuCores cores | $abi"
        } catch (_: Exception) {
            "${Build.HARDWARE} | ${Runtime.getRuntime().availableProcessors()} cores"
        }
    }

    // ── CPU usage (dynamic, from /proc/stat) ───────────────────────────

    /**
     * Parses a single "cpu" or "cpuN" line from /proc/stat.
     * Returns [user, nice, system, idle, iowait, irq, softirq, steal] as a LongArray.
     */
    private fun parseCpuLine(line: String): LongArray {
        val parts = line.trim().split("\\s+".toRegex())
        // parts[0] = "cpu" or "cpu0" etc., parts[1..] = numbers
        return LongArray(minOf(parts.size - 1, 8)) { i ->
            parts.getOrNull(i + 1)?.toLongOrNull() ?: 0L
        }
    }

    private data class CpuSnapshot(
        val total: LongArray,
        val perCore: List<LongArray>
    )

    private fun readCpuSnapshot(): CpuSnapshot? {
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

    private fun initCpuSnapshot() {
        val snap = readCpuSnapshot() ?: return
        prevTotalCpu = snap.total
        prevPerCoreCpu = snap.perCore
        buildCoreRows(snap.perCore.size)
    }

    private fun refreshCpuUsage() {
        val snap = readCpuSnapshot() ?: return
        val prevTotal = prevTotalCpu
        val prevCores = prevPerCoreCpu

        if (prevTotal != null) {
            val overallPct = calcUsagePercent(prevTotal, snap.total)
            tvCpuUsagePct.text = "$overallPct%"
            pbCpuUsage.progress = overallPct
            updateProgressBarColor(pbCpuUsage, overallPct)
        }

        if (prevCores != null) {
            val rows = coreRows ?: return
            for (i in rows.indices) {
                if (i < snap.perCore.size && i < prevCores.size) {
                    val pct = calcUsagePercent(prevCores[i], snap.perCore[i])
                    rows[i].label.text = String.format(Locale.getDefault(), "Core %d", i)
                    rows[i].pctText.text = String.format(Locale.getDefault(), "%3d%%", pct)
                    rows[i].bar.progress = pct
                    updateProgressBarColor(rows[i].bar, pct)
                }
            }
        }

        prevTotalCpu = snap.total
        prevPerCoreCpu = snap.perCore
    }

    private fun updateProgressBarColor(bar: ProgressBar, pct: Int) {
        val color = when {
            pct >= 80 -> 0xFFFF4444.toInt() // red
            pct >= 50 -> 0xFFFFFF00.toInt() // yellow
            else -> 0xFF00FF88.toInt()       // neon green
        }
        bar.progressDrawable?.setTint(color)
    }

    private data class CoreRow(val label: TextView, val bar: ProgressBar, val pctText: TextView)

    private fun buildCoreRows(count: Int) {
        llCpuCores.removeAllViews()
        val rows = mutableListOf<CoreRow>()
        val barHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()
        val marginTopPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()

        for (i in 0 until count) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = marginTopPx }
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = String.format(Locale.getDefault(), "Core %d", i)
                setTextColor(0x88FFFFFF.toInt())
                textSize = 10f
                typeface = Typeface.MONOSPACE
            }

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, barHeightPx, 1f).apply {
                    marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                    marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                }
                max = 100
                progressDrawable = resources.getDrawable(R.drawable.cpu_progress_bar, null).mutate()
            }

            val pctText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "  0%"
                setTextColor(0xFF00FF88.toInt())
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextAppearance(android.R.style.TextAppearance_Small)
                setTextColor(0xFF00FF88.toInt())
                textSize = 10f
            }

            row.addView(label)
            row.addView(bar)
            row.addView(pctText)
            llCpuCores.addView(row)
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

        tvMemoryPct.text = "$pct%"
        pbMemory.progress = pct
        updateProgressBarColor(pbMemory, pct)
        tvMemory.text = String.format(Locale.getDefault(), "%.1f GB / %.1f GB (%.1f GB free)", usedGB, totalGB, availGB)
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

        tvDiskPct.text = "$pct%"
        pbDisk.progress = pct
        updateProgressBarColor(pbDisk, pct)
        tvDisk.text = String.format(Locale.getDefault(), "%.1f GB / %.1f GB (%.1f GB free)", usedGB, totalGB, availGB)
    }

    // ── Network ────────────────────────────────────────────────────────

    private fun getNetworkInfo(): String {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Not connected"
        val caps = cm.getNetworkCapabilities(network) ?: return "Not connected"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Connected"
        }
    }

    // ── Uptime ─────────────────────────────────────────────────────────

    private fun getUptime(): String {
        val uptimeMs = SystemClock.elapsedRealtime()
        val seconds = (uptimeMs / 1000) % 60
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val hours = (uptimeMs / (1000 * 60 * 60)) % 24
        val days = uptimeMs / (1000 * 60 * 60 * 24)
        return if (days > 0) {
            String.format(Locale.getDefault(), "%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)
        }
    }
}
