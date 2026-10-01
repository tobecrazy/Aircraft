package com.young.aircraft.gui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.system.Os
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.young.aircraft.R
import com.young.aircraft.gui.dialogs.InfoDialogContent
import com.young.aircraft.gui.dialogs.setDialogComposeContent
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.aircraftSwitchColors
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.utils.DebugTools
import java.io.File
import androidx.core.net.toUri

// Panel visuals lifted from develop_settings_panel_bg / device_info_gauge_bg /
// the Theme.Aircraft.Common colorPrimary that styled the legacy buttons.
private val PanelBg = Color(0x22252A3A)
private val PanelBorder = Color(0x2200FF88)
private val BadgeBg = Color(0x18FFFFFF)
private val BadgeText = Color(0xFF8CC6FF)
private val ButtonBg = Color(0xFF252A3A)

/** Tag on the module LazyColumn; tests scroll it with performScrollToNode. */
internal const val ModuleListTag = "assistant_module_list"

/** Kernel facts for the debug kernel-info module. */
internal data class KernelInfo(
    val release: String,
    val machine: String,
    val fullVersion: String
)

/**
 * Os.uname() first (structured, no permissions), falling back to the os.version
 * system property; /proc/version is best-effort because SELinux blocks it on some devices.
 */
internal fun readKernelInfo(): KernelInfo {
    val uname = runCatching { Os.uname() }.getOrNull()
    if (uname != null) {
        return KernelInfo(
            release = uname.release,
            machine = uname.machine,
            fullVersion = "${uname.sysname} ${uname.release} ${uname.version}"
        )
    }
    val procVersion = runCatching { File("/proc/version").readText().trim() }.getOrNull()
    return KernelInfo(
        release = System.getProperty("os.version") ?: "unknown",
        machine = System.getProperty("os.arch") ?: "unknown",
        fullVersion = procVersion ?: "unknown"
    )
}

/** WebView / installed-browser facts for the debug browser-engine module. */
internal data class BrowserEngineInfo(
    val webViewPackage: String,
    val webViewVersion: String,
    val chromiumMajor: String,
    val userAgent: String,
    val defaultBrowser: String,
    val installedBrowsers: List<String>
)

/**
 * Native APIs only: WebView.getCurrentWebViewPackage (API 26+) already reads the provider
 * package, so androidx.webkit is not needed just for that.
 */
internal fun readBrowserEngineInfo(context: Context): BrowserEngineInfo {
    val webViewPkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
    val userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
    val pm = context.packageManager

    val defaultBrowser = runCatching {
        pm.resolveActivity(
            Intent(Intent.ACTION_VIEW, "https://example.com".toUri()),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }.getOrNull() ?: "unknown"

    val installed = runCatching {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, "https://example.com".toUri()),
            PackageManager.MATCH_DEFAULT_ONLY
        )
    }.getOrNull().orEmpty()
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .map { pkg ->
            val version = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
            if (version.isNullOrBlank()) pkg else "$pkg ($version)"
        }
        .sorted()

    return BrowserEngineInfo(
        webViewPackage = webViewPkg?.packageName ?: "unknown",
        webViewVersion = webViewPkg?.versionName ?: "unknown",
        chromiumMajor = Regex("""Chrome/(\d+)""").find(userAgent)?.groupValues?.get(1) ?: "unknown",
        userAgent = userAgent,
        defaultBrowser = defaultBrowser,
        installedBrowsers = installed
    )
}

/** Static descriptor of an assistant tool row (state lives in SharedPreferences). */
internal data class AssistantModule(
    val prefKey: String,
    val labelRes: Int,
    val descriptionRes: Int,
    val actionRes: Int
)

class AndroidDevAssistantToolsActivity : AppCompatActivity() {

    private lateinit var assistantPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        assistantPrefs = getSharedPreferences(ASSISTANT_PREFS, MODE_PRIVATE)

        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                AndroidDevAssistantToolsScreen(
                    initialStates = ASSISTANT_MODULES.associate { it.prefKey to isModuleEnabled(it.prefKey) },
                    onBack = { finish() },
                    onToggle = ::setModuleEnabled,
                    onOpenModule = ::openModule
                )
            }
        }
    }

    private fun isModuleEnabled(module: String): Boolean = assistantPrefs.getBoolean(module, true)

    private fun setModuleEnabled(module: String, enabled: Boolean) {
        assistantPrefs.edit { putBoolean(module, enabled) }
        val msg =
            if (enabled) R.string.develop_settings_assistant_module_on
            else R.string.develop_settings_assistant_module_off
        ThemedMessage.makeText(this, msg, ThemedMessage.LENGTH_SHORT).show()
    }

    internal fun openModule(prefKey: String) {
        if (!isModuleEnabled(prefKey)) {
            ThemedMessage.makeText(this, R.string.develop_settings_assistant_module_off, ThemedMessage.LENGTH_SHORT).show()
            return
        }
        when (prefKey) {
            MODULE_SYSTEM_INFO -> startActivity(Intent(this, DeviceInfoActivity::class.java))

            MODULE_QUICK_SETTINGS -> {
                val launched = launchSafely(Intent(Settings.ACTION_SETTINGS)) ||
                    launchSafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                if (!launched) {
                    ThemedMessage.makeText(this, R.string.develop_settings_assistant_unavailable, ThemedMessage.LENGTH_SHORT).show()
                }
            }

            MODULE_APP_BROWSER -> showAppListDialog()

            MODULE_ACTIVITY_MONITOR -> startActivity(Intent(this, HistoryActivity::class.java))

            MODULE_KERNEL_INFO -> showKernelInfoDialog()

            MODULE_BROWSER_ENGINE -> showBrowserEngineDialog()
        }
    }

    private fun showKernelInfoDialog() {
        val info = readKernelInfo()
        showInfoDialog(
            title = getString(R.string.develop_settings_assistant_kernel_dialog_title),
            body = getString(
                R.string.develop_settings_assistant_kernel_dialog_message,
                info.release,
                info.machine,
                info.fullVersion
            )
        )
    }

    private fun showBrowserEngineDialog() {
        val info = readBrowserEngineInfo(this)
        val browsers = info.installedBrowsers.joinToString("\n")
            .ifBlank { getString(R.string.develop_settings_assistant_browser_none) }
        showInfoDialog(
            title = getString(R.string.develop_settings_assistant_browser_dialog_title),
            body = getString(
                R.string.develop_settings_assistant_browser_dialog_message,
                info.webViewPackage,
                info.webViewVersion,
                info.chromiumMajor,
                info.defaultBrowser,
                info.userAgent,
                browsers
            )
        )
    }

    private fun showAppListDialog() {
        val dialog = AlertDialog.Builder(this).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)
        dialog.setDialogComposeContent(this) { AppListDialogContent() }
    }

    /** Monospace scrollable panel; native message dialogs truncate these dumps. */
    private fun showInfoDialog(title: String, body: String) {
        val dialog = AlertDialog.Builder(this).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)
        dialog.setDialogComposeContent(this) {
            InfoDialogContent(
                title = title,
                body = body,
                closeText = getString(android.R.string.ok),
                copyText = getString(R.string.qr_code_tool_copy),
                copiedText = getString(R.string.qr_code_tool_copied)
            )
        }
    }

    private fun launchSafely(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        private const val ASSISTANT_PREFS = "android_dev_assistant_prefs"
        internal const val MODULE_SYSTEM_INFO = "module_system_info"
        internal const val MODULE_QUICK_SETTINGS = "module_quick_settings"
        internal const val MODULE_APP_BROWSER = "module_app_browser"
        internal const val MODULE_ACTIVITY_MONITOR = "module_activity_monitor"
        internal const val MODULE_KERNEL_INFO = "module_kernel_info"
        internal const val MODULE_BROWSER_ENGINE = "module_browser_engine"

        internal val ASSISTANT_MODULES = listOf(
            AssistantModule(
                prefKey = MODULE_SYSTEM_INFO,
                labelRes = R.string.develop_settings_assistant_module_system_info,
                descriptionRes = R.string.android_dev_assistant_tools_system_info,
                actionRes = R.string.develop_settings_assistant_action_system_info
            ),
            AssistantModule(
                prefKey = MODULE_QUICK_SETTINGS,
                labelRes = R.string.develop_settings_assistant_module_quick_settings,
                descriptionRes = R.string.android_dev_assistant_tools_quick_settings,
                actionRes = R.string.develop_settings_assistant_action_quick_settings
            ),
            AssistantModule(
                prefKey = MODULE_APP_BROWSER,
                labelRes = R.string.develop_settings_assistant_module_app_browser,
                descriptionRes = R.string.android_dev_assistant_tools_app_browser,
                actionRes = R.string.develop_settings_assistant_action_app_browser
            ),
            AssistantModule(
                prefKey = MODULE_ACTIVITY_MONITOR,
                labelRes = R.string.develop_settings_assistant_module_activity_monitor,
                descriptionRes = R.string.android_dev_assistant_tools_activity_monitor,
                actionRes = R.string.develop_settings_assistant_action_activity_monitor
            ),
            AssistantModule(
                prefKey = MODULE_KERNEL_INFO,
                labelRes = R.string.develop_settings_assistant_module_kernel_info,
                descriptionRes = R.string.android_dev_assistant_tools_kernel_info,
                actionRes = R.string.develop_settings_assistant_action_kernel_info
            ),
            AssistantModule(
                prefKey = MODULE_BROWSER_ENGINE,
                labelRes = R.string.develop_settings_assistant_module_browser_engine,
                descriptionRes = R.string.android_dev_assistant_tools_browser_engine,
                actionRes = R.string.develop_settings_assistant_action_browser_engine
            )
        )
    }
}

@Composable
internal fun AndroidDevAssistantToolsScreen(
    initialStates: Map<String, Boolean>,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenModule: (String) -> Unit
) {
    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply { putAll(initialStates) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        AssistantHeader(onBack = onBack)
        NeonDivider()

        // LazyColumn, not Column+scroll: each module row is ~150dp, so this list is
        // taller than any phone screen and only the visible rows should be composed.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag(ModuleListTag),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "intro") {
                IntroPanel(Modifier.maxContentWidth().padding(horizontal = 14.dp))
            }

            items(
                count = AndroidDevAssistantToolsActivity.ASSISTANT_MODULES.size,
                key = { AndroidDevAssistantToolsActivity.ASSISTANT_MODULES[it].prefKey }
            ) { index ->
                val module = AndroidDevAssistantToolsActivity.ASSISTANT_MODULES[index]
                ModuleRow(
                    module = module,
                    enabled = enabledStates[module.prefKey] ?: true,
                    onToggle = { value ->
                        enabledStates[module.prefKey] = value
                        onToggle(module.prefKey, value)
                    },
                    onOpenModule = { onOpenModule(module.prefKey) },
                    modifier = Modifier
                        .maxContentWidth()
                        .padding(horizontal = 14.dp)
                )
            }

            item(key = "footer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit) {

    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
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
            text = stringResource(R.string.android_dev_assistant_tools_title),
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
private fun IntroPanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = PanelBg,
        border = BorderStroke(1.dp, PanelBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.develop_settings_assistant_badge),
                color = BadgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(BadgeBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.android_dev_assistant_tools_summary),
                color = TextBright,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ModuleRow(
    module: AssistantModule,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenModule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = PanelBg,
        border = BorderStroke(1.dp, PanelBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(module.labelRes),
                color = TextBright,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("assistant_switch_${module.prefKey}"),
                colors = aircraftSwitchColors()
            )
        }

        Text(
            text = stringResource(module.descriptionRes),
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )

        ActionButton(
            text = stringResource(module.actionRes),
            enabled = enabled,
            onClick = onOpenModule,
            modifier = Modifier
                .padding(top = 10.dp)
                .testTag("assistant_open_${module.prefKey}")
        )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBg,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1118, widthDp = 412, heightDp = 892)
@Composable
private fun AndroidDevAssistantToolsScreenPreview() {
    AircraftTheme {
        AndroidDevAssistantToolsScreen(
            initialStates = AndroidDevAssistantToolsActivity.ASSISTANT_MODULES.associate { it.prefKey to true },
            onBack = {},
            onToggle = { _, _ -> },
            onOpenModule = {}
        )
    }
}
