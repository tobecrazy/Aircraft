package com.young.aircraft.gui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.young.aircraft.R
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.TextMuted
import com.young.aircraft.utils.DebugTools

// Panel visuals lifted from develop_settings_panel_bg / device_info_gauge_bg /
// the Theme.Aircraft.Common colorPrimary that styled the legacy buttons.
private val PanelBg = Color(0x22252A3A)
private val PanelBorder = Color(0x2200FF88)
private val BadgeBg = Color(0x18FFFFFF)
private val BadgeText = Color(0xFF8CC6FF)
private val ButtonBg = Color(0xFF252A3A)
private val SwitchTrackChecked = Color(0x6600FF88)
private val SwitchTrackUnchecked = Color(0x33FFFFFF)

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
        assistantPrefs.edit().putBoolean(module, enabled).apply()
        val msg =
            if (enabled) R.string.develop_settings_assistant_module_on
            else R.string.develop_settings_assistant_module_off
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    internal fun openModule(prefKey: String) {
        if (!isModuleEnabled(prefKey)) {
            Toast.makeText(this, R.string.develop_settings_assistant_module_off, Toast.LENGTH_SHORT).show()
            return
        }
        when (prefKey) {
            MODULE_SYSTEM_INFO -> startActivity(Intent(this, DeviceInfoActivity::class.java))

            MODULE_QUICK_SETTINGS -> {
                val launched = launchSafely(Intent(Settings.ACTION_SETTINGS)) ||
                    launchSafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                if (!launched) {
                    Toast.makeText(this, R.string.develop_settings_assistant_unavailable, Toast.LENGTH_SHORT).show()
                }
            }

            MODULE_APP_BROWSER -> {
                val launched = launchSafely(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                if (!launched) {
                    Toast.makeText(this, R.string.develop_settings_assistant_unavailable, Toast.LENGTH_SHORT).show()
                }
            }

            MODULE_ACTIVITY_MONITOR -> startActivity(Intent(this, HistoryActivity::class.java))
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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Caps and centers on wide screens, matching MaxWidthLinearLayout peers.
            Column(
                modifier = Modifier
                    .maxContentWidth()
                    .padding(horizontal = 14.dp)
            ) {
                IntroPanel()

                Spacer(modifier = Modifier.height(12.dp))

                ModulesPanel(
                    enabledStates = enabledStates,
                    onToggle = { key, value ->
                        enabledStates[key] = value
                        onToggle(key, value)
                    },
                    onOpenModule = onOpenModule
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
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
private fun IntroPanel() {
    Surface(
        modifier = Modifier
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
private fun ModulesPanel(
    enabledStates: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    onOpenModule: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PanelBg,
        border = BorderStroke(1.dp, PanelBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AndroidDevAssistantToolsActivity.ASSISTANT_MODULES.forEachIndexed { index, module ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                ModuleRow(
                    module = module,
                    enabled = enabledStates[module.prefKey] ?: true,
                    onToggle = { onToggle(module.prefKey, it) },
                    onOpenModule = { onOpenModule(module.prefKey) }
                )
            }
        }
    }
}

@Composable
private fun ModuleRow(
    module: AssistantModule,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenModule: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentGreen,
                    checkedTrackColor = SwitchTrackChecked,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SwitchTrackUnchecked,
                    uncheckedBorderColor = Color.Transparent
                )
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
