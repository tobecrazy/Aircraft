package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.databinding.ActivityDevelopSettingsBinding
import com.young.aircraft.providers.SettingsRepository

class DevelopSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevelopSettingsBinding
    private lateinit var settingsRepository: SettingsRepository
    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        title = getString(R.string.develop_settings_title)
        binding = ActivityDevelopSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsRepository = SettingsRepository(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBuildBadge.text = getString(R.string.develop_settings_debug_badge)
        binding.tvVersionBadge.text = getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        binding.tvVersionBadge.setOnClickListener {
            clickCount++
            if (clickCount >= 8) {
                clickCount = 0
                binding.switchInvincible.toggle()
            }
        }

        binding.tvSummary.text = getString(R.string.develop_settings_banner_summary)
        setupInvincibleMode()
        binding.btnTestCrash.setOnClickListener {
            throw RuntimeException("Test Crash") // Force a crash
        }

        binding.btnTestRichText.setOnClickListener {
            startActivity(Intent(this, RichTextEditorActivity::class.java))
        }
    }

    private fun setupInvincibleMode() {
        val isInvincible = settingsRepository.isInvincibleModeEnabled()
        GameStateManager.isInvincible = isInvincible
        binding.switchInvincible.isChecked = isInvincible
        updateInvincibleUi(isInvincible)

        binding.switchInvincible.setOnCheckedChangeListener { _, enabled ->
            settingsRepository.setInvincibleModeEnabled(enabled)
            GameStateManager.isInvincible = enabled
            updateInvincibleUi(enabled)

            val msg = if (enabled) R.string.invincible_mode_on else R.string.invincible_mode_off
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateInvincibleUi(enabled: Boolean) {
        binding.tvInvincibleChip.text = getString(
            if (enabled) R.string.develop_settings_invincible_status_on
            else R.string.develop_settings_invincible_status_off
        )
        binding.tvInvincibleChip.setBackgroundResource(
            if (enabled) R.drawable.develop_settings_status_active_bg
            else R.drawable.develop_settings_status_inactive_bg
        )
        binding.tvInvincibleRuntimeStatus.text = getString(
            if (enabled) R.string.develop_settings_invincible_runtime_on
            else R.string.develop_settings_invincible_runtime_off
        )
    }
}
