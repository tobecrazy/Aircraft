package com.young.aircraft.gui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.young.aircraft.R
import com.young.aircraft.viewmodel.BatteryState
import com.young.aircraft.viewmodel.CoreUsage
import com.young.aircraft.viewmodel.CpuState
import com.young.aircraft.viewmodel.DeviceInfoUiState
import com.young.aircraft.viewmodel.DeviceInfoViewModel
import com.young.aircraft.viewmodel.DiskState
import com.young.aircraft.viewmodel.MemoryState
import com.young.aircraft.viewmodel.NetworkState
import com.young.aircraft.viewmodel.NetworkType
import com.young.aircraft.viewmodel.TimeState
import com.young.aircraft.viewmodel.formatBytes
import kotlinx.coroutines.launch

class DeviceInfoActivity : AppCompatActivity() {

    private lateinit var viewModel: DeviceInfoViewModel

    // True while a foldable's hinge reports FLAT (device unfolded) — widens the System Info rows.
    private val systemInfoWide = mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            viewModel.refreshDynamicInfo()
            handler.postDelayed(this, 1000L)
        }
    }

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

        viewModel = ViewModelProvider(this, DeviceInfoViewModel.Factory(this))[DeviceInfoViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        viewModel.initStaticInfo()
        viewModel.initCpuSnapshot()
        viewModel.initTrafficSnapshot()
        viewModel.refreshDynamicInfo()

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()
                DeviceInfoScreen(
                    uiState = uiState,
                    systemInfoWide = systemInfoWide.value,
                    onBack = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@DeviceInfoActivity)
                    .windowLayoutInfo(this@DeviceInfoActivity)
                    .collect { layoutInfo ->
                        val foldFeature = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                        systemInfoWide.value =
                            foldFeature != null && foldFeature.state == FoldingFeature.State.FLAT
                    }
            }
        }
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
}

// ── Tactical palette (matches peer screens) ──────────────────────────────

private val BgDark = Color(0xFF0F1118)
private val HeaderBg = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val DividerGreen = Color(0x4400FF88)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFCDD2E0)
private val TextMuted = Color(0xFFAAB4C8)
private val LabelMuted = Color(0x88FFFFFF)
private val Red = Color(0xFFFF4444)
private val Yellow = Color(0xFFFFFF00)

private fun pctColor(pct: Int): Color = when {
    pct >= 80 -> Red
    pct >= 50 -> Yellow
    else -> AccentGreen
}

private fun batteryPctColor(pct: Int): Color = when {
    pct <= 15 -> Red
    pct <= 30 -> Yellow
    else -> TextPrimary
}

// ── Screen ───────────────────────────────────────────────────────────────

@Composable
fun DeviceInfoScreen(
    uiState: DeviceInfoUiState,
    systemInfoWide: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .safeDrawingPadding()
    ) {
        MonitorHeader(onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerGreen))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag("device_info_scroll")
                .padding(horizontal = 14.dp)
        ) {
            HeroCard(uiState.staticInfo, uiState.time)
            SectionHeader(R.string.device_info_section_resources)
            CpuCard(cpu = uiState.cpu, cpuInfo = uiState.staticInfo.cpuInfo)
            MemoryDiskRow(uiState.memory, uiState.disk)
            BatteryCard(uiState.battery)
            NetworkCard(uiState.network)
            SectionHeader(R.string.device_info_section_system)
            SystemInfoCard(uiState.staticInfo, systemInfoWide = systemInfoWide)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonitorHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBg)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.title_activity_device_info),
            modifier = Modifier.align(Alignment.Center),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(AccentGreen)
        )
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(start = 8.dp),
            color = Color(0x66FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun HeroCard(staticInfo: com.young.aircraft.viewmodel.DeviceStaticInfo, time: TimeState) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(
                Brush.linearGradient(listOf(Color(0x2E152033), Color(0x1E162B28))),
                shape
            )
            .border(1.dp, Color(0x3300FF88), shape)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        GaugeChip(
            text = stringResource(R.string.device_info_title),
            textColor = Color(0xFFD8E0EF),
            textSize = 11.sp
        )
        Text(
            text = staticInfo.deviceModel,
            modifier = Modifier.padding(top = 14.dp),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            GaugeChip(
                text = staticInfo.androidVersion,
                textColor = AccentGreen,
                textSize = 11.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            GaugeChip(
                text = staticInfo.appVersion,
                textColor = Color(0xFFD8E0EF),
                textSize = 11.sp
            )
        }
        Text(
            text = stringResource(R.string.device_info_summary),
            modifier = Modifier.padding(top = 10.dp),
            color = TextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp
        )
        InfoItem(
            labelRes = R.string.device_info_current_time,
            value = time.currentTime,
            valueColor = AccentGreen,
            valueSize = 14.sp,
            valueBold = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
        InfoItem(
            labelRes = R.string.device_info_uptime,
            value = time.uptime,
            valueColor = TextPrimary,
            valueSize = 14.sp,
            valueBold = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun CpuCard(cpu: CpuState, cpuInfo: String) {
    CardBox(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.device_info_cpu),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = cpuInfo,
                        modifier = Modifier.padding(top = 6.dp),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.device_info_fmt_pct, cpu.overallPct),
                        color = AccentGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        GaugeChip(
                            text = cpuTempText(cpu.temperature),
                            textColor = if (cpu.temperature == null) Color(0x55FFFFFF) else TextPrimary,
                            textSize = 10.sp,
                            bold = false
                        )
                    }
                }
            }
            PercentBar(pct = cpu.overallPct, modifier = Modifier.padding(top = 12.dp))
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cpu.coreUsages.forEach { core ->
                    CoreUsageRow(core)
                }
            }
        }
    }
}

@Composable
private fun CoreUsageRow(core: CoreUsage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1A252A3A), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(LocalLocale.current.platformLocale, "C%02d", core.index),
            color = LabelMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        PercentBar(
            pct = if (core.isOnline) core.pct else 0,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            barHeight = 4.dp
        )
        Text(
            text = corePctText(core),
            color = AccentGreen,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun corePctText(core: CoreUsage): String = when {
    !core.isOnline -> stringResource(R.string.device_info_cpu_core_off)
    core.freqMhz != null -> stringResource(R.string.device_info_fmt_core_pct_freq, core.pct, core.freqMhz)
    else -> stringResource(R.string.device_info_fmt_core_pct, core.pct)
}

@Composable
private fun cpuTempText(temp: Float?): String =
    if (temp != null) {
        stringResource(R.string.device_info_fmt_cpu_temp, temp)
    } else {
        stringResource(R.string.device_info_cpu_temp_na)
    }

@Composable
private fun MemoryDiskRow(memory: MemoryState, disk: DiskState) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        ResourceStatCard(
            labelRes = R.string.device_info_memory,
            pct = memory.pct,
            detail = stringResource(
                R.string.device_info_fmt_memory_detail,
                memory.usedGB, memory.availGB, memory.totalGB,
                memory.buffersGB, memory.cachedGB
            ),
            modifier = Modifier.weight(1f).padding(end = 5.dp)
        )
        ResourceStatCard(
            labelRes = R.string.device_info_disk,
            pct = disk.pct,
            detail = stringResource(
                R.string.device_info_fmt_disk_detail,
                disk.usedGB, disk.availGB, disk.totalGB
            ),
            modifier = Modifier.weight(1f).padding(start = 5.dp)
        )
    }
}

@Composable
private fun ResourceStatCard(
    labelRes: Int,
    pct: Int,
    detail: String,
    modifier: Modifier = Modifier
) {
    CardBox(modifier = modifier) {
        Column {
            ItemLabel(labelRes)
            Text(
                text = stringResource(R.string.device_info_fmt_pct, pct),
                modifier = Modifier.padding(top = 8.dp),
                color = pctColor(pct),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            PercentBar(pct = pct, modifier = Modifier.padding(top = 10.dp))
            Text(
                text = detail,
                modifier = Modifier.padding(top = 10.dp),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BatteryCard(battery: BatteryState) {
    CardBox(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column {
            ItemLabel(R.string.device_info_battery)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.device_info_fmt_pct, battery.pct),
                    color = batteryPctColor(battery.pct),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(10.dp))
                GaugeChip(
                    text = stringResource(
                        if (battery.isCharging) R.string.device_info_battery_charging
                        else R.string.device_info_battery_discharging
                    ),
                    textColor = Color(0xFFD8E0EF),
                    textSize = 10.sp,
                    bold = false
                )
            }
        }
    }
}

@Composable
private fun NetworkCard(network: NetworkState) {
    CardBox(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column {
            ItemLabel(R.string.device_info_network)
            Text(
                text = networkName(network.type),
                modifier = Modifier.padding(top = 8.dp),
                color = if (network.type == NetworkType.OFFLINE) Red else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = network.detail,
                modifier = Modifier.padding(top = 4.dp),
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            if (network.type != NetworkType.OFFLINE) {
                Text(
                    text = if (network.throughputReady) {
                        stringResource(
                            R.string.device_info_net_throughput,
                            formatBytes(network.rxBytesPerSec),
                            formatBytes(network.txBytesPerSec)
                        )
                    } else {
                        stringResource(R.string.device_info_net_throughput_init)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = stringResource(R.string.device_info_net_ip, network.ipAddress),
                    modifier = Modifier.padding(top = 4.dp),
                    color = LabelMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun networkName(type: NetworkType): String = stringResource(
    when (type) {
        NetworkType.OFFLINE -> R.string.device_info_net_offline
        NetworkType.WIFI -> R.string.device_info_net_wifi
        NetworkType.CELLULAR -> R.string.device_info_net_cellular
        NetworkType.ETHERNET -> R.string.device_info_net_ethernet
        NetworkType.OTHER -> R.string.device_info_net_online
    }
)

@Composable
private fun SystemInfoCard(
    staticInfo: com.young.aircraft.viewmodel.DeviceStaticInfo,
    systemInfoWide: Boolean
) {
    CardBox(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (systemInfoWide) {
            // Foldable unfolded: screen info + boot time share one row.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoItem(
                    labelRes = R.string.device_info_screen_resolution,
                    value = staticInfo.screenResolution,
                    valueColor = TextSecondary,
                    valueSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                InfoItem(
                    labelRes = R.string.device_info_boot_time,
                    value = staticInfo.bootTime,
                    valueColor = TextSecondary,
                    valueSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column {
                InfoItem(
                    labelRes = R.string.device_info_screen_resolution,
                    value = staticInfo.screenResolution,
                    valueColor = TextSecondary,
                    valueSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                InfoItem(
                    labelRes = R.string.device_info_boot_time,
                    value = staticInfo.bootTime,
                    valueColor = TextSecondary,
                    valueSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                )
            }
        }
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .background(Color(0x20252A3A), shape)
            .border(1.dp, Color(0x2200FF88), shape)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun ItemBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .background(Color(0x1A252A3A), shape)
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        content()
    }
}

@Composable
private fun ItemLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        color = LabelMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun InfoItem(
    labelRes: Int,
    value: String,
    valueColor: Color,
    valueSize: androidx.compose.ui.unit.TextUnit,
    valueBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    ItemBox(modifier = modifier) {
        Column {
            ItemLabel(labelRes)
            Text(
                text = value,
                modifier = Modifier.padding(top = 6.dp),
                color = valueColor,
                fontSize = valueSize,
                fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun GaugeChip(
    text: String,
    textColor: Color,
    textSize: androidx.compose.ui.unit.TextUnit,
    bold: Boolean = true
) {
    Text(
        text = text,
        modifier = Modifier
            .background(Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = textColor,
        fontSize = textSize,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun PercentBar(pct: Int, modifier: Modifier = Modifier, barHeight: Dp = 6.dp) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Color(0x1AFFFFFF), shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth((pct.coerceIn(0, 100)) / 100f)
                .background(pctColor(pct), shape)
        )
    }
}

// ── Preview ──────────────────────────────────────────────────────────────

private fun previewUiState(): DeviceInfoUiState = DeviceInfoUiState(
    staticInfo = com.young.aircraft.viewmodel.DeviceStaticInfo(
        deviceModel = "GOOGLE PIXEL 9",
        androidVersion = "Android 15 (API 35)",
        cpuInfo = "Tensor G4 | 8 cores | arm64-v8a",
        screenResolution = "1080 x 2340 @ 420dpi",
        bootTime = "2026-08-22 08:30:00",
        appVersion = "v1.2.9"
    ),
    cpu = CpuState(
        overallPct = 34,
        coreUsages = List(8) { i ->
            CoreUsage(index = i, pct = (i * 11) % 100, freqMhz = 1800 + i * 100)
        },
        temperature = 45.7f
    ),
    memory = MemoryState(pct = 61, usedGB = 5.2, availGB = 3.1, totalGB = 8.0, buffersGB = 0.31, cachedGB = 1.2),
    disk = DiskState(pct = 47, usedGB = 48.1, availGB = 54.0, totalGB = 102.4),
    network = NetworkState(
        type = NetworkType.WIFI,
        detail = "HOME_5G 300Mbps RSSI:-52dBm [4/4]",
        rxBytesPerSec = 245760,
        txBytesPerSec = 40960,
        ipAddress = "192.168.1.23",
        throughputReady = true
    ),
    battery = BatteryState(pct = 76, isCharging = false),
    time = TimeState(
        currentTime = "2026-08-22 08:39:47",
        uptime = "1 day 02:03:04"
    )
)

@Preview(showBackground = true, widthDp = 420, heightDp = 960)
@Composable
private fun DeviceInfoScreenPreview() {
    MaterialTheme {
        DeviceInfoScreen(uiState = previewUiState(), systemInfoWide = false, onBack = {})
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 840)
@Composable
private fun DeviceInfoScreenUnfoldedPreview() {
    MaterialTheme {
        DeviceInfoScreen(uiState = previewUiState(), systemInfoWide = true, onBack = {})
    }
}
