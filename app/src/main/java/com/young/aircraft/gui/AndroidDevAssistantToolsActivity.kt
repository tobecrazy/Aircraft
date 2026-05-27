package com.young.aircraft.gui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityAndroidDevAssistantToolsBinding
import com.young.aircraft.utils.DebugTools

class AndroidDevAssistantToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAndroidDevAssistantToolsBinding
    private lateinit var assistantPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }

        title = getString(R.string.android_dev_assistant_tools_title)
        binding = ActivityAndroidDevAssistantToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        assistantPrefs = getSharedPreferences(ASSISTANT_PREFS, MODE_PRIVATE)

        binding.btnBack.setOnClickListener { finish() }
        setupAssistantTools()
    }

    private fun setupAssistantTools() {
        val modules = listOf(
            AssistantModule(
                prefKey = MODULE_SYSTEM_INFO,
                moduleLabelRes = R.string.develop_settings_assistant_module_system_info,
                toggle = binding.switchAssistantSystemInfo,
                actionButton = binding.btnAssistantOpenSystemInfo,
                action = { startActivity(Intent(this, DeviceInfoActivity::class.java)) }
            ),
            AssistantModule(
                prefKey = MODULE_QUICK_SETTINGS,
                moduleLabelRes = R.string.develop_settings_assistant_module_quick_settings,
                toggle = binding.switchAssistantQuickSettings,
                actionButton = binding.btnAssistantOpenQuickSettings,
                action = {
                    val launched = launchSafely(Intent(Settings.ACTION_SETTINGS)) ||
                        launchSafely(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    if (!launched) {
                        Toast.makeText(this, R.string.develop_settings_assistant_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            ),
            AssistantModule(
                prefKey = MODULE_APP_BROWSER,
                moduleLabelRes = R.string.develop_settings_assistant_module_app_browser,
                toggle = binding.switchAssistantAppBrowser,
                actionButton = binding.btnAssistantOpenAppBrowser,
                action = {
                    val launched = launchSafely(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                    if (!launched) {
                        Toast.makeText(this, R.string.develop_settings_assistant_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            ),
            AssistantModule(
                prefKey = MODULE_ACTIVITY_MONITOR,
                moduleLabelRes = R.string.develop_settings_assistant_module_activity_monitor,
                toggle = binding.switchAssistantActivityMonitor,
                actionButton = binding.btnAssistantOpenActivityMonitor,
                action = { startActivity(Intent(this, HistoryActivity::class.java)) }
            )
        )

        modules.forEach(::bindModule)
    }

    private fun bindModule(module: AssistantModule) {
        val isEnabled = isModuleEnabled(module.prefKey)
        module.toggle.isChecked = isEnabled
        module.actionButton.isEnabled = isEnabled

        module.toggle.setOnCheckedChangeListener { _, enabled ->
            setModuleEnabled(module.prefKey, enabled)
            module.actionButton.isEnabled = enabled
        }

        module.actionButton.setOnClickListener {
            if (!isModuleEnabled(module.prefKey)) {
                Toast.makeText(this, R.string.develop_settings_assistant_module_off, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            module.action.invoke()
        }

        module.actionButton.contentDescription = getString(module.moduleLabelRes)
    }

    private fun isModuleEnabled(module: String): Boolean = assistantPrefs.getBoolean(module, true)

    private fun setModuleEnabled(module: String, enabled: Boolean) {
        assistantPrefs.edit { putBoolean(module, enabled) }
        val msg = if (enabled) R.string.develop_settings_assistant_module_on else R.string.develop_settings_assistant_module_off
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
        private const val MODULE_SYSTEM_INFO = "module_system_info"
        private const val MODULE_QUICK_SETTINGS = "module_quick_settings"
        private const val MODULE_APP_BROWSER = "module_app_browser"
        private const val MODULE_ACTIVITY_MONITOR = "module_activity_monitor"
    }

    private data class AssistantModule(
        val prefKey: String,
        val moduleLabelRes: Int,
        val toggle: SwitchCompat,
        val actionButton: Button,
        val action: () -> Unit
    )
}
