package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.young.aircraft.R
import com.young.aircraft.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

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

        fun updateSelection(value: String) {
            val options = mapOf("1.2" to optionEasy, "1.0" to optionNormal, "0.8" to optionHard)
            options.forEach { (key, view) -> view.isSelected = key == value }

            val dot = binding.root.findViewById<View>(R.id.current_indicator_dot)
            val label = binding.root.findViewById<TextView>(R.id.current_selection_label)
            label.text = getString(R.string.difficulty_current, getDifficultyLabel(value))
            dot.setBackgroundResource(
                when (value) {
                    "1.2" -> R.drawable.difficulty_indicator_easy
                    "0.8" -> R.drawable.difficulty_indicator_hard
                    else -> R.drawable.difficulty_indicator_normal
                }
            )
        }

        fun select(value: String) {
            prefs.edit().putString("difficulty", value).apply()
            updateSelection(value)
        }

        updateSelection(getCurrentDifficulty())

        optionEasy.setOnClickListener { select("1.2") }
        optionNormal.setOnClickListener { select("1.0") }
        optionHard.setOnClickListener { select("0.8") }
    }

    private fun getCurrentDifficulty(): String =
        prefs.getString("difficulty", "1.0") ?: "1.0"

    private fun getDifficultyLabel(value: String): String = when (value) {
        "1.2" -> getString(R.string.difficulty_easy)
        "0.8" -> getString(R.string.difficulty_hard)
        else -> getString(R.string.difficulty_normal)
    }

    // ── Sound toggles ──────────────────────────────────────

    private fun setupSoundToggles() {
        val switchBg = binding.root.findViewById<SwitchCompat>(R.id.switch_bg_sound)
        val statusBg = binding.root.findViewById<TextView>(R.id.tv_bg_sound_status)
        val switchCombat = binding.root.findViewById<SwitchCompat>(R.id.switch_combat_sound)
        val statusCombat = binding.root.findViewById<TextView>(R.id.tv_combat_sound_status)

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

        switchBg.isChecked = prefs.getBoolean("background_sound", true)
        updateBgStatus(switchBg.isChecked)
        switchBg.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_sound", isChecked).apply()
            updateBgStatus(isChecked)
        }

        switchCombat.isChecked = prefs.getBoolean("combat_sound", true)
        updateCombatStatus(switchCombat.isChecked)
        switchCombat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("combat_sound", isChecked).apply()
            updateCombatStatus(isChecked)
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
        binding.root.findViewById<LinearLayout>(R.id.row_privacy_policy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }
}
