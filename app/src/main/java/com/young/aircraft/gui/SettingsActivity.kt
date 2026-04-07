package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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
        val optionEasy = binding.root.findViewById<LinearLayout>(R.id.option_easy)
        val optionNormal = binding.root.findViewById<LinearLayout>(R.id.option_normal)
        val optionHard = binding.root.findViewById<LinearLayout>(R.id.option_hard)

        fun updateSelection(difficulty: GameDifficulty) {
            val options = mapOf(
                GameDifficulty.EASY to optionEasy,
                GameDifficulty.NORMAL to optionNormal,
                GameDifficulty.HARD to optionHard
            )
            options.forEach { (key, view) -> view.isSelected = key == difficulty }

            val dot = binding.root.findViewById<View>(R.id.current_indicator_dot)
            val label = binding.root.findViewById<TextView>(R.id.current_selection_label)
            label.text = getString(R.string.difficulty_current, getDifficultyLabel(difficulty))
            dot.setBackgroundResource(
                when (difficulty) {
                    GameDifficulty.EASY -> R.drawable.difficulty_indicator_easy
                    GameDifficulty.HARD -> R.drawable.difficulty_indicator_hard
                    else -> R.drawable.difficulty_indicator_normal
                }
            )
        }

        fun select(difficulty: GameDifficulty) {
            settingsRepository.setDifficulty(difficulty)
            updateSelection(difficulty)
        }

        updateSelection(getCurrentDifficulty())

        optionEasy.setOnClickListener { select(GameDifficulty.EASY) }
        optionNormal.setOnClickListener { select(GameDifficulty.NORMAL) }
        optionHard.setOnClickListener { select(GameDifficulty.HARD) }
    }

    private fun getCurrentDifficulty(): GameDifficulty = settingsRepository.getDifficulty()

    private fun getDifficultyLabel(value: GameDifficulty): String = when (value) {
        GameDifficulty.EASY -> getString(R.string.difficulty_easy)
        GameDifficulty.HARD -> getString(R.string.difficulty_hard)
        else -> getString(R.string.difficulty_normal)
    }

    // ── Sound toggles ──────────────────────────────────────

    private fun setupSoundToggles() {
        val switchBg = binding.root.findViewById<SwitchCompat>(R.id.switch_bg_sound)
        val statusBg = binding.root.findViewById<TextView>(R.id.tv_bg_sound_status)
        val switchCombat = binding.root.findViewById<SwitchCompat>(R.id.switch_combat_sound)
        val statusCombat = binding.root.findViewById<TextView>(R.id.tv_combat_sound_status)
        val switchHitShake = binding.root.findViewById<SwitchCompat>(R.id.switch_hit_shake)
        val statusHitShake = binding.root.findViewById<TextView>(R.id.tv_hit_shake_status)

        fun updateBgStatus(on: Boolean) {
            statusBg.text = getString(
                if (on) R.string.background_sound_summary_on
                else R.string.background_sound_summary_off
            )
        }

        fun updateCombatStatus(on: Boolean) {
            statusCombat.text = getString(
                if (on) R.string.combat_sound_summary_on
                else R.string.combat_sound_summary_off
            )
        }

        fun updateHitShakeStatus(on: Boolean) {
            statusHitShake.text = getString(
                if (on) R.string.hit_shake_effect_summary_on
                else R.string.hit_shake_effect_summary_off
            )
        }

        switchBg.isChecked = settingsRepository.isBackgroundSoundEnabled()
        updateBgStatus(switchBg.isChecked)
        switchBg.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setBackgroundSoundEnabled(isChecked)
            updateBgStatus(isChecked)
        }

        switchCombat.isChecked = settingsRepository.isCombatSoundEnabled()
        updateCombatStatus(switchCombat.isChecked)
        switchCombat.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setCombatSoundEnabled(isChecked)
            updateCombatStatus(isChecked)
        }

        switchHitShake.isChecked = settingsRepository.isHitShakeEffectEnabled()
        updateHitShakeStatus(switchHitShake.isChecked)
        switchHitShake.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setHitShakeEffectEnabled(isChecked)
            updateHitShakeStatus(isChecked)
        }
    }

    // ── Navigation rows ────────────────────────────────────

    private fun setupNavigation() {
        binding.root.findViewById<LinearLayout>(R.id.row_device_info).setOnClickListener {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
        }
        binding.root.findViewById<LinearLayout>(R.id.row_about_aircraft).setOnClickListener {
            startActivity(Intent(this, AboutAircraftActivity::class.java))
        }
        binding.root.findViewById<LinearLayout>(R.id.row_about_me).setOnClickListener {
            startActivity(Intent(this, AboutMeActivity::class.java))
        }
        binding.root.findViewById<LinearLayout>(R.id.row_privacy_policy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        val developDivider = binding.root.findViewById<View>(R.id.divider_develop_settings)
        val developSettingsRow = binding.root.findViewById<LinearLayout>(R.id.row_develop_settings)
        if (BuildConfig.DEBUG) {
            developDivider.visibility = View.VISIBLE
            developSettingsRow.visibility = View.VISIBLE
            developSettingsRow.setOnClickListener {
                startActivity(Intent(this, DevelopSettingsActivity::class.java))
            }
        }
    }
}
