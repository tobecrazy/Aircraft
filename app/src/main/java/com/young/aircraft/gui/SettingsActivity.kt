package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.databinding.SettingsActivityBinding
import com.young.aircraft.providers.SettingsRepository
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

        val repository = SettingsRepository(this)
        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(repository))[SettingsViewModel::class.java]

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
