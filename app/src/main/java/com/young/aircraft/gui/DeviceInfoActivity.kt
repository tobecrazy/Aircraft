package com.young.aircraft.gui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityDeviceInfoBinding
import com.young.aircraft.viewmodel.CoreUsage
import com.young.aircraft.viewmodel.DeviceInfoUiState
import com.young.aircraft.viewmodel.DeviceInfoViewModel
import com.young.aircraft.viewmodel.NetworkType
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceInfoBinding
    private lateinit var viewModel: DeviceInfoViewModel

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.refreshDynamicInfo()
            handler.postDelayed(this, 1000L)
        }
    }

    private var coreRows: List<CoreRow>? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100 / scale) else 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
            viewModel.updateBattery(pct, charging)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, DeviceInfoViewModel.Factory(this))[DeviceInfoViewModel::class.java]

        binding.btnBack.setOnClickListener { finish() }

        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbMemory.progressDrawable = it }
        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbDisk.progressDrawable = it }
        ResourcesCompat.getDrawable(resources, R.drawable.cpu_progress_bar, null)?.mutate()?.let { binding.pbCpuUsage.progressDrawable = it }

        viewModel.initStaticInfo()
        viewModel.initCpuSnapshot()
        viewModel.initTrafficSnapshot()
        buildCoreRows(viewModel.coreCount)
        viewModel.refreshDynamicInfo()

        observeState()
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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: DeviceInfoUiState) {
        // Static info
        binding.tvDeviceModel.text = state.staticInfo.deviceModel
        binding.tvAndroidVersion.text = state.staticInfo.androidVersion
        binding.tvCpu.text = state.staticInfo.cpuInfo
        binding.tvBootTime.text = state.staticInfo.bootTime
        binding.tvScreenResolution.text = state.staticInfo.screenResolution
        binding.tvAppVersion.text = state.staticInfo.appVersion

        // CPU
        binding.tvCpuUsagePct.text = getString(R.string.device_info_fmt_pct, state.cpu.overallPct)
        binding.pbCpuUsage.progress = state.cpu.overallPct
        updateProgressBarColor(binding.pbCpuUsage, state.cpu.overallPct)
        renderCpuCores(state.cpu.coreUsages)
        renderCpuTemp(state.cpu.temperature)

        // Memory
        binding.tvMemoryPct.text = getString(R.string.device_info_fmt_pct, state.memory.pct)
        binding.tvMemoryPct.setTextColor(pctColor(state.memory.pct))
        binding.pbMemory.progress = state.memory.pct
        updateProgressBarColor(binding.pbMemory, state.memory.pct)
        binding.tvMemory.text = String.format(
            Locale.getDefault(),
            getString(R.string.device_info_fmt_memory_detail),
            state.memory.usedGB, state.memory.availGB, state.memory.totalGB,
            state.memory.buffersGB, state.memory.cachedGB
        )

        // Disk
        binding.tvDiskPct.text = getString(R.string.device_info_fmt_pct, state.disk.pct)
        binding.tvDiskPct.setTextColor(pctColor(state.disk.pct))
        binding.pbDisk.progress = state.disk.pct
        updateProgressBarColor(binding.pbDisk, state.disk.pct)
        binding.tvDisk.text = String.format(
            Locale.getDefault(),
            getString(R.string.device_info_fmt_disk_detail),
            state.disk.usedGB, state.disk.availGB, state.disk.totalGB
        )

        // Network
        renderNetwork(state)

        // Battery
        binding.tvBattery.text = getString(R.string.device_info_fmt_pct, state.battery.pct)
        binding.tvBattery.setTextColor(
            when {
                state.battery.pct <= 15 -> 0xFFFF4444.toInt()
                state.battery.pct <= 30 -> 0xFFFFFF00.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        )
        binding.tvBatteryStatus.text = if (state.battery.isCharging)
            getString(R.string.device_info_battery_charging)
        else
            getString(R.string.device_info_battery_discharging)

        // Time
        binding.tvCurrentTime.text = state.time.currentTime
        binding.tvUptime.text = state.time.uptime
    }

    private fun renderCpuCores(coreUsages: List<CoreUsage>) {
        val rows = coreRows ?: return
        for (i in rows.indices) {
            if (i >= coreUsages.size) break
            val core = coreUsages[i]
            if (core.isOnline) {
                if (core.freqMhz != null) {
                    rows[i].pctText.text = getString(R.string.device_info_fmt_core_pct_freq, core.pct, core.freqMhz)
                } else {
                    rows[i].pctText.text = getString(R.string.device_info_fmt_core_pct, core.pct)
                }
                rows[i].bar.progress = core.pct
                updateProgressBarColor(rows[i].bar, core.pct)
            } else {
                rows[i].pctText.text = getString(R.string.device_info_cpu_core_off)
                rows[i].bar.progress = 0
                updateProgressBarColor(rows[i].bar, 0)
            }
        }
    }

    private fun renderCpuTemp(temp: Float?) {
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

    private fun renderNetwork(state: DeviceInfoUiState) {
        val net = state.network
        when (net.type) {
            NetworkType.OFFLINE -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_offline)
                binding.tvNetwork.setTextColor(0xFFFF4444.toInt())
                binding.tvNetworkDetail.text = getString(R.string.device_info_net_no_connection)
                binding.tvNetworkThroughput.text = ""
                binding.tvNetworkExtra.text = ""
            }
            NetworkType.WIFI -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_wifi)
                binding.tvNetwork.setTextColor(0xFFFFFFFF.toInt())
                binding.tvNetworkDetail.text = net.detail
                renderThroughput(net)
                binding.tvNetworkExtra.text = getString(R.string.device_info_net_ip, net.ipAddress)
            }
            NetworkType.CELLULAR -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_cellular)
                binding.tvNetwork.setTextColor(0xFFFFFFFF.toInt())
                binding.tvNetworkDetail.text = net.detail
                renderThroughput(net)
                binding.tvNetworkExtra.text = getString(R.string.device_info_net_ip, net.ipAddress)
            }
            NetworkType.ETHERNET -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_ethernet)
                binding.tvNetwork.setTextColor(0xFFFFFFFF.toInt())
                binding.tvNetworkDetail.text = net.detail
                renderThroughput(net)
                binding.tvNetworkExtra.text = getString(R.string.device_info_net_ip, net.ipAddress)
            }
            NetworkType.OTHER -> {
                binding.tvNetwork.text = getString(R.string.device_info_net_online)
                binding.tvNetwork.setTextColor(0xFFFFFFFF.toInt())
                binding.tvNetworkDetail.text = net.detail
                renderThroughput(net)
                binding.tvNetworkExtra.text = getString(R.string.device_info_net_ip, net.ipAddress)
            }
        }
    }

    private fun renderThroughput(net: com.young.aircraft.viewmodel.NetworkState) {
        binding.tvNetworkThroughput.text = if (net.throughputReady) {
            getString(
                R.string.device_info_net_throughput,
                viewModel.formatBytes(net.rxBytesPerSec),
                viewModel.formatBytes(net.txBytesPerSec)
            )
        } else {
            getString(R.string.device_info_net_throughput_init)
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private fun updateProgressBarColor(bar: ProgressBar, pct: Int) {
        val color = when {
            pct >= 80 -> 0xFFFF4444.toInt()
            pct >= 50 -> 0xFFFFFF00.toInt()
            else -> 0xFF00FF88.toInt()
        }
        bar.progressDrawable?.setTint(color)
    }

    private fun pctColor(pct: Int): Int = when {
        pct >= 80 -> 0xFFFF4444.toInt()
        pct >= 50 -> 0xFFFFFF00.toInt()
        else -> 0xFF00FF88.toInt()
    }

    private data class CoreRow(val label: TextView, val bar: ProgressBar, val pctText: TextView)

    private fun buildCoreRows(count: Int) {
        binding.llCpuCores.removeAllViews()
        val rows = mutableListOf<CoreRow>()
        val dp = resources.displayMetrics
        val barH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, dp).toInt()
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, dp).toInt()
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dp).toInt()
        val rowPadH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, dp).toInt()
        val rowPadV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, dp).toInt()

        for (i in 0 until count) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = gap }
                background = ResourcesCompat.getDrawable(resources, R.drawable.device_info_item_bg, null)
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = String.format(Locale.getDefault(), "C%02d", i)
                setTextColor(0x88FFFFFF.toInt())
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
}
