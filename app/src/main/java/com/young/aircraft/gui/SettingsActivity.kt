package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.gui.dialogs.GameDialogContent
import com.young.aircraft.gui.dialogs.GameDialogStat
import com.young.aircraft.gui.dialogs.setDialogComposeContent
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(this))[SettingsViewModel::class.java]

        setContent {
            AircraftTheme {
                val state by viewModel.uiState.collectAsState()
                SettingsScreen(
                    state = state,
                    onBack = { finish() },
                    onThemeSelected = viewModel::setTheme,
                    onNavigate = ::navigateTo,
                    onClearCache = ::showClearCacheDialog
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hero card chips show difficulty/sound state that may have changed on GameSettingsActivity.
        viewModel.refresh()
    }

    private fun navigateTo(destination: SettingsDestination) {
        val target = when (destination) {
            SettingsDestination.GAME_SETTINGS -> GameSettingsActivity::class.java
            SettingsDestination.DEVICE_INFO -> DeviceInfoActivity::class.java
            SettingsDestination.QR_CODE_TOOL -> QRCodeToolActivity::class.java
            SettingsDestination.FLASHLIGHT -> FlashlightActivity::class.java
            SettingsDestination.PUZZLE -> PuzzleActivity::class.java
            SettingsDestination.ABOUT_AIRCRAFT -> AboutAircraftActivity::class.java
            SettingsDestination.ABOUT_ME -> AboutMeActivity::class.java
            SettingsDestination.PRIVACY_POLICY -> PrivacyPolicyActivity::class.java
            SettingsDestination.DEVELOP_SETTINGS -> DevelopSettingsActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun showClearCacheDialog() {
        lifecycleScope.launch {
            val cacheSizeBytes = runCatching {
                viewModel.getCachedGameDataSizeBytes()
            }.getOrDefault(0L)
            showClearCacheDialog(formatCacheSize(cacheSizeBytes))
        }
    }

    private fun showClearCacheDialog(cacheSize: String) {
        val dialog = AlertDialog.Builder(this)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)

        dialog.setDialogComposeContent(this) {
            GameDialogContent(
                badgeText = getString(R.string.clear_cache_badge),
                title = getString(R.string.clear_cache_dialog_title),
                message = getString(R.string.clear_cache_dialog_message),
                primaryStat = GameDialogStat(
                    label = getString(R.string.clear_cache_size_label),
                    value = cacheSize
                ),
                secondaryStat = GameDialogStat(
                    label = getString(R.string.clear_cache_keep_label),
                    value = getString(R.string.clear_cache_keep_value)
                ),
                positiveText = getString(R.string.clear_cache_confirm),
                onPositive = {
                    dialog.dismiss()
                    clearCachedGameData()
                },
                negativeText = getString(R.string.history_cancel)
            )
        }
    }

    private fun formatCacheSize(bytes: Long): String {
        return if (bytes <= 0L) {
            getString(R.string.clear_cache_empty_size)
        } else {
            Formatter.formatShortFileSize(this, bytes)
        }
    }

    private fun clearCachedGameData() {
        lifecycleScope.launch {
            val result = runCatching {
                viewModel.clearCachedGameData()
                BitmapUtils.clearCaches()
            }
            val messageRes = if (result.isSuccess) {
                R.string.clear_cache_success
            } else {
                R.string.clear_cache_failed
            }
            ThemedMessage.makeText(this@SettingsActivity, messageRes, ThemedMessage.LENGTH_SHORT).show()
        }
    }
}
