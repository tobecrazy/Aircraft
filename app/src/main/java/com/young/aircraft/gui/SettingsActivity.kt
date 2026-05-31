package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.databinding.SettingsActivityBinding
import com.young.aircraft.utils.BitmapUtils
import com.young.aircraft.viewmodel.SettingsUiState
import com.young.aircraft.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private lateinit var viewModel: SettingsViewModel
    private val soundOptionCount = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(this))[SettingsViewModel::class.java]

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.optionEasy.setOnClickListener { viewModel.setDifficulty(GameDifficulty.EASY) }
        binding.optionNormal.setOnClickListener { viewModel.setDifficulty(GameDifficulty.NORMAL) }
        binding.optionHard.setOnClickListener { viewModel.setDifficulty(GameDifficulty.HARD) }

        binding.rowBgSound.setOnClickListener { binding.switchBgSound.toggle() }
        binding.switchBgSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBgSoundEnabled(isChecked)
        }

        binding.rowCombatSound.setOnClickListener { binding.switchCombatSound.toggle() }
        binding.switchCombatSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCombatSoundEnabled(isChecked)
        }

        binding.rowHitShake.setOnClickListener { binding.switchHitShake.toggle() }
        binding.switchHitShake.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHitShakeEnabled(isChecked)
        }

        binding.rowDeviceInfo.setOnClickListener {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
        }
        binding.rowQrCodeTool.setOnClickListener {
            startActivity(Intent(this, QRCodeToolActivity::class.java))
        }
        binding.rowFlashlight.setOnClickListener {
            startActivity(Intent(this, FlashlightActivity::class.java))
        }
        binding.rowPuzzleGame.setOnClickListener {
            startActivity(Intent(this, PuzzleActivity::class.java))
        }
        binding.rowClearCache.setOnClickListener {
            showClearCacheDialog()
        }
        binding.rowAboutAircraft.setOnClickListener {
            startActivity(Intent(this, AboutAircraftActivity::class.java))
        }
        binding.rowAboutMe.setOnClickListener {
            startActivity(Intent(this, AboutMeActivity::class.java))
        }
        binding.rowPrivacyPolicy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        binding.rowDevelopSettings.setOnClickListener {
            startActivity(Intent(this, DevelopSettingsActivity::class.java))
        }
    }

    private fun showClearCacheDialog() {
        binding.rowClearCache.isEnabled = false
        lifecycleScope.launch {
            val cacheSizeBytes = runCatching {
                viewModel.getCachedGameDataSizeBytes()
            }.getOrDefault(0L)
            binding.rowClearCache.isEnabled = true
            showClearCacheDialog(formatCacheSize(cacheSizeBytes))
        }
    }

    private fun showClearCacheDialog(cacheSize: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_game, null)
        dialogView.findViewById<TextView>(R.id.dialog_badge).apply {
            visibility = View.VISIBLE
            text = getString(R.string.clear_cache_badge)
            setBackgroundResource(R.drawable.dialog_badge_danger_bg)
        }
        dialogView.findViewById<TextView>(R.id.dialog_title).apply {
            text = getString(R.string.clear_cache_dialog_title)
            setTextColor(0xFFFF6F7E.toInt())
        }
        dialogView.findViewById<View>(R.id.dialog_divider).setBackgroundColor(0x44FF4444)
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            getString(R.string.clear_cache_dialog_message)
        dialogView.findViewById<LinearLayout>(R.id.dialog_stats_container).visibility = View.VISIBLE
        dialogView.findViewById<LinearLayout>(R.id.stat_card_1)
            .setBackgroundResource(R.drawable.dialog_stat_card_danger_bg)
        dialogView.findViewById<LinearLayout>(R.id.stat_card_2)
            .setBackgroundResource(R.drawable.dialog_stat_card_bg)
        dialogView.findViewById<TextView>(R.id.stat_label_1).apply {
            text = getString(R.string.clear_cache_size_label)
            setTextColor(0x88FF6F7E.toInt())
        }
        dialogView.findViewById<TextView>(R.id.stat_value_1).text = cacheSize
        dialogView.findViewById<TextView>(R.id.stat_label_2).apply {
            text = getString(R.string.clear_cache_keep_label)
            setTextColor(0x8800FF88.toInt())
        }
        dialogView.findViewById<TextView>(R.id.stat_value_2).text =
            getString(R.string.clear_cache_keep_value)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)

        dialogView.findViewById<TextView>(R.id.dialog_negative_btn).apply {
            visibility = View.VISIBLE
            text = getString(R.string.history_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        dialogView.findViewById<TextView>(R.id.dialog_positive_btn).apply {
            text = getString(R.string.clear_cache_confirm)
            setBackgroundResource(R.drawable.dialog_button_primary_danger)
            setOnClickListener {
                dialog.dismiss()
                clearCachedGameData()
            }
        }
        dialog.show()
    }

    private fun formatCacheSize(bytes: Long): String {
        return if (bytes <= 0L) {
            getString(R.string.clear_cache_empty_size)
        } else {
            Formatter.formatShortFileSize(this, bytes)
        }
    }

    private fun clearCachedGameData() {
        binding.rowClearCache.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                viewModel.clearCachedGameData()
                BitmapUtils.clearCaches()
            }
            binding.rowClearCache.isEnabled = true
            val messageRes = if (result.isSuccess) {
                R.string.clear_cache_success
            } else {
                R.string.clear_cache_failed
            }
            Toast.makeText(this@SettingsActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
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

    private fun renderState(state: SettingsUiState) {
        renderDifficulty(state.difficulty)
        renderSoundToggles(state)
        renderSoundOverview(state.enabledSoundCount)
        binding.rowDevelopSettings.visibility = if (state.showDevelopSettings) View.VISIBLE else View.GONE
    }

    private fun renderDifficulty(difficulty: GameDifficulty) {
        binding.optionEasy.isSelected = difficulty == GameDifficulty.EASY
        binding.optionNormal.isSelected = difficulty == GameDifficulty.NORMAL
        binding.optionHard.isSelected = difficulty == GameDifficulty.HARD

        val label = getDifficultyLabel(difficulty)
        binding.currentSelectionLabel.text = getString(R.string.difficulty_current, label)
        binding.currentIndicatorDot.setBackgroundResource(
            when (difficulty) {
                GameDifficulty.EASY -> R.drawable.difficulty_indicator_easy
                GameDifficulty.HARD -> R.drawable.difficulty_indicator_hard
                else -> R.drawable.difficulty_indicator_normal
            }
        )
        binding.tvActiveDifficultyChip.text = getString(R.string.settings_profile_chip, label)
    }

    private fun renderSoundToggles(state: SettingsUiState) {
        renderToggle(binding.switchBgSound, state.bgSoundEnabled,
            binding.tvBgSoundStatus, R.string.background_sound_summary_on, R.string.background_sound_summary_off,
            binding.tvBgSoundChip)
        renderToggle(binding.switchCombatSound, state.combatSoundEnabled,
            binding.tvCombatSoundStatus, R.string.combat_sound_summary_on, R.string.combat_sound_summary_off,
            binding.tvCombatSoundChip)
        renderToggle(binding.switchHitShake, state.hitShakeEnabled,
            binding.tvHitShakeStatus, R.string.hit_shake_effect_summary_on, R.string.hit_shake_effect_summary_off,
            binding.tvHitShakeChip)
    }

    private fun renderToggle(
        switch: androidx.appcompat.widget.SwitchCompat,
        enabled: Boolean,
        status: android.widget.TextView,
        onTextRes: Int,
        offTextRes: Int,
        chip: android.widget.TextView
    ) {
        if (switch.isChecked != enabled) {
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = enabled
            switch.setOnCheckedChangeListener { _, isChecked ->
                when (switch.id) {
                    R.id.switch_bg_sound -> viewModel.setBgSoundEnabled(isChecked)
                    R.id.switch_combat_sound -> viewModel.setCombatSoundEnabled(isChecked)
                    R.id.switch_hit_shake -> viewModel.setHitShakeEnabled(isChecked)
                }
            }
        }
        status.text = getString(if (enabled) onTextRes else offTextRes)
        chip.text = getString(if (enabled) R.string.settings_state_on else R.string.settings_state_off)
        chip.setBackgroundResource(
            if (enabled) R.drawable.settings_chip_active_bg else R.drawable.settings_chip_bg
        )
    }

    private fun renderSoundOverview(enabledCount: Int) {
        binding.tvSoundProfileChip.text = getString(
            R.string.settings_sound_profile_chip, enabledCount, soundOptionCount
        )
        binding.tvSoundProfileChip.setBackgroundResource(
            if (enabledCount > 0) R.drawable.settings_chip_active_bg else R.drawable.settings_chip_bg
        )
        binding.tvSoundSectionChip.text = getString(
            R.string.settings_sound_active_count, enabledCount, soundOptionCount
        )
        binding.tvSoundSectionChip.setBackgroundResource(
            if (enabledCount > 0) R.drawable.settings_chip_active_bg else R.drawable.settings_chip_bg
        )
    }

    private fun getDifficultyLabel(value: GameDifficulty): String = when (value) {
        GameDifficulty.EASY -> getString(R.string.difficulty_easy)
        GameDifficulty.HARD -> getString(R.string.difficulty_hard)
        else -> getString(R.string.difficulty_normal)
    }
}
