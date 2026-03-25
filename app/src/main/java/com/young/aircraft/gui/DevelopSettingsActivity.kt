package com.young.aircraft.gui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityDevelopSettingsBinding

class DevelopSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevelopSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        title = getString(R.string.develop_settings_title)
        binding = ActivityDevelopSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBuildBadge.text = getString(R.string.develop_settings_debug_badge)
        binding.tvVersionBadge.text = getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        binding.tvSummary.text = getString(R.string.develop_settings_banner_summary)
        binding.btnTestCrash.setOnClickListener {
            throw RuntimeException("Test Crash") // Force a crash
        }
    }
}
