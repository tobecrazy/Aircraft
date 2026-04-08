package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.databinding.SettingsActivityBinding
import com.young.aircraft.providers.SettingsRepository

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private lateinit var settingsRepository: SettingsRepository
    private val soundOptionCount = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsRepository = SettingsRepository(this)

        setupHeader()
        setupDifficulty()
        setupSoundToggles()
        setupNavigation()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── Difficulty ──────────────────────────────────────────

    private fun setupDifficulty() {
        fun updateSelection(difficulty: GameDifficulty) {
            val options = mapOf(
                GameDifficulty.EASY to binding.optionEasy,
                GameDifficulty.NORMAL to binding.optionNormal,
                GameDifficulty.HARD to binding.optionHard
            )
            options.forEach { (key, view) -> view.isSelected = key == difficulty }

            binding.currentSelectionLabel.text =
                getString(R.string.difficulty_current, getDifficultyLabel(difficulty))
            binding.currentIndicatorDot.setBackgroundResource(
                when (difficulty) {
                    GameDifficulty.EASY -> R.drawable.difficulty_indicator_easy
                    GameDifficulty.HARD -> R.drawable.difficulty_indicator_hard
                    else -> R.drawable.difficulty_indicator_normal
                }
            )
            binding.tvActiveDifficultyChip.text = getString(
                R.string.settings_profile_chip,
                getDifficultyLabel(difficulty)
            )
        }

        fun select(difficulty: GameDifficulty) {
            settingsRepository.setDifficulty(difficulty)
            updateSelection(difficulty)
        }

        updateSelection(getCurrentDifficulty())

        binding.optionEasy.setOnClickListener { select(GameDifficulty.EASY) }
        binding.optionNormal.setOnClickListener { select(GameDifficulty.NORMAL) }
        binding.optionHard.setOnClickListener { select(GameDifficulty.HARD) }
    }

    private fun getCurrentDifficulty(): GameDifficulty = settingsRepository.getDifficulty()

    private fun getDifficultyLabel(value: GameDifficulty): String = when (value) {
        GameDifficulty.EASY -> getString(R.string.difficulty_easy)
        GameDifficulty.HARD -> getString(R.string.difficulty_hard)
        else -> getString(R.string.difficulty_normal)
    }

    // ── Sound toggles ──────────────────────────────────────

    private fun setupSoundToggles() {
        setupSoundToggle(
            row = binding.rowBgSound,
            switch = binding.switchBgSound,
            status = binding.tvBgSoundStatus,
            chip = binding.tvBgSoundChip,
            initialValue = settingsRepository.isBackgroundSoundEnabled(),
            onTextRes = R.string.background_sound_summary_on,
            offTextRes = R.string.background_sound_summary_off,
            persist = settingsRepository::setBackgroundSoundEnabled
        )
        setupSoundToggle(
            row = binding.rowCombatSound,
            switch = binding.switchCombatSound,
            status = binding.tvCombatSoundStatus,
            chip = binding.tvCombatSoundChip,
            initialValue = settingsRepository.isCombatSoundEnabled(),
            onTextRes = R.string.combat_sound_summary_on,
            offTextRes = R.string.combat_sound_summary_off,
            persist = settingsRepository::setCombatSoundEnabled
        )
        setupSoundToggle(
            row = binding.rowHitShake,
            switch = binding.switchHitShake,
            status = binding.tvHitShakeStatus,
            chip = binding.tvHitShakeChip,
            initialValue = settingsRepository.isHitShakeEffectEnabled(),
            onTextRes = R.string.hit_shake_effect_summary_on,
            offTextRes = R.string.hit_shake_effect_summary_off,
            persist = settingsRepository::setHitShakeEffectEnabled
        )
        updateSoundOverview()
    }

    // ── Navigation rows ────────────────────────────────────

    private fun setupNavigation() {
        binding.rowDeviceInfo.setOnClickListener {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
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

        if (BuildConfig.DEBUG) {
            binding.rowDevelopSettings.visibility = View.VISIBLE
            binding.rowDevelopSettings.setOnClickListener {
                startActivity(Intent(this, DevelopSettingsActivity::class.java))
            }
        }
    }

    private fun setupSoundToggle(
        row: View,
        switch: SwitchCompat,
        status: TextView,
        chip: TextView,
        initialValue: Boolean,
        onTextRes: Int,
        offTextRes: Int,
        persist: (Boolean) -> Unit
    ) {
        fun update(enabled: Boolean) {
            status.text = getString(if (enabled) onTextRes else offTextRes)
            chip.text = getString(if (enabled) R.string.settings_state_on else R.string.settings_state_off)
            chip.setBackgroundResource(
                if (enabled) R.drawable.settings_chip_active_bg
                else R.drawable.settings_chip_bg
            )
            updateSoundOverview()
        }

        switch.isChecked = initialValue
        update(initialValue)
        row.setOnClickListener { switch.toggle() }
        switch.setOnCheckedChangeListener { _, isChecked ->
            persist(isChecked)
            update(isChecked)
        }
    }

    private fun updateSoundOverview() {
        val enabledCount = listOf(
            binding.switchBgSound,
            binding.switchCombatSound,
            binding.switchHitShake
        ).count { it.isChecked }

        binding.tvSoundProfileChip.text = getString(
            R.string.settings_sound_profile_chip,
            enabledCount,
            soundOptionCount
        )
        binding.tvSoundProfileChip.setBackgroundResource(
            if (enabledCount > 0) R.drawable.settings_chip_active_bg
            else R.drawable.settings_chip_bg
        )
        binding.tvSoundSectionChip.text = getString(
            R.string.settings_sound_active_count,
            enabledCount,
            soundOptionCount
        )
        binding.tvSoundSectionChip.setBackgroundResource(
            if (enabledCount > 0) R.drawable.settings_chip_active_bg
            else R.drawable.settings_chip_bg
        )
    }
}
