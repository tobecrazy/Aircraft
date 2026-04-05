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
                val isInvincible = !settingsRepository.isInvincibleModeEnabled()
                GameStateManager.isInvincible = isInvincible
                settingsRepository.setInvincibleModeEnabled(isInvincible)
                
                val msg = if (isInvincible) R.string.invincible_mode_on else R.string.invincible_mode_off
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvSummary.text = getString(R.string.develop_settings_banner_summary)
        binding.btnTestCrash.setOnClickListener {
            throw RuntimeException("Test Crash") // Force a crash
        }

        binding.btnTestRichText.setOnClickListener {
            startActivity(Intent(this, RichTextEditorActivity::class.java))
        }
    }
}
