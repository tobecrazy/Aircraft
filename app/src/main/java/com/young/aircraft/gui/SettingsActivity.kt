package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.gui.dialogs.DangerPalette
import com.young.aircraft.gui.dialogs.GameDialogContent
import com.young.aircraft.gui.dialogs.GameDialogStat
import com.young.aircraft.gui.dialogs.setDialogComposeContent
import androidx.compose.ui.graphics.Color
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val soundOptionCount = 3
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(this))[SettingsViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsState()
            SettingsScreen(
                state = state,
                soundOptionCount = soundOptionCount,
                onBack = { finish() },
                onDifficultySelected = viewModel::setDifficulty,
                onBgSoundToggled = viewModel::setBgSoundEnabled,
                onCombatSoundToggled = viewModel::setCombatSoundEnabled,
                onHitShakeToggled = viewModel::setHitShakeEnabled,
                onBgmFormatSelected = viewModel::setBgmFormat,
                onNavigate = ::navigateTo,
                onClearCache = ::showClearCacheDialog
            )
        }
    }

    private fun navigateTo(destination: SettingsDestination) {
        val target = when (destination) {
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

        // Danger palette with the legacy per-slot overrides: softer title red, mixed stat cards.
        dialog.setDialogComposeContent(this) {
            GameDialogContent(
                badgeText = getString(R.string.clear_cache_badge),
                palette = DangerPalette.copy(titleColor = Color(0xFFFF6F7E)),
                title = getString(R.string.clear_cache_dialog_title),
                message = getString(R.string.clear_cache_dialog_message),
                primaryStat = GameDialogStat(
                    label = getString(R.string.clear_cache_size_label),
                    value = cacheSize
                ),
                secondaryStat = GameDialogStat(
                    label = getString(R.string.clear_cache_keep_label),
                    value = getString(R.string.clear_cache_keep_value),
                    labelColor = Color(0x8800FF88),
                    cardContainer = Color(0x18FFFFFF),
                    cardBorder = Color(0x22FFFFFF)
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
            Toast.makeText(this@SettingsActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
    }
}
