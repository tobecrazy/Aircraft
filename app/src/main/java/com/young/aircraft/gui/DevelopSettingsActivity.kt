package com.young.aircraft.gui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.databinding.ActivityDevelopSettingsBinding
import com.young.aircraft.viewmodel.DevelopSettingsViewModel

class DevelopSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevelopSettingsBinding
    private lateinit var viewModel: DevelopSettingsViewModel
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
        viewModel = ViewModelProvider(this, DevelopSettingsViewModel.Factory(this))[DevelopSettingsViewModel::class.java]

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
        setupSupperBanner()
        binding.btnTestCrash.setOnClickListener {
            throw RuntimeException("Test Crash") // Force a crash
        }

        binding.btnTestRichText.setOnClickListener {
            startActivity(Intent(this, RichTextEditorActivity::class.java))
        }
    }

    private fun setupSupperBanner() {
        val backgroundItems = listOf(
            SupperBannerItem(
                name = "background.jpg",
                description = getString(R.string.develop_settings_supper_banner_local_description),
                image = SupperBannerImage.Local(R.drawable.background)
            ),
            SupperBannerItem(
                name = "background_1.jpg",
                description = getString(R.string.develop_settings_supper_banner_local_description),
                image = SupperBannerImage.Local(R.drawable.background_1)
            ),
            SupperBannerItem(
                name = "background_2.jpg",
                description = getString(R.string.develop_settings_supper_banner_local_description),
                image = SupperBannerImage.Local(R.drawable.background_2)
            ),
            SupperBannerItem(
                name = "background_3.jpg",
                description = getString(R.string.develop_settings_supper_banner_local_description),
                image = SupperBannerImage.Local(R.drawable.background_3)
            ),
            SupperBannerItem(
                name = "background_4.jpg",
                description = getString(R.string.develop_settings_supper_banner_local_description),
                image = SupperBannerImage.Local(R.drawable.background_4)
            ),
            SupperBannerItem(
                name = "network_background",
                description = getString(R.string.develop_settings_supper_banner_network_description),
                image = SupperBannerImage.Network("https://picsum.photos/960/540?aircraft-background")
            )
        )

        binding.supperBanner.apply {
            setItems(backgroundItems)
            setAutoPlayEnabled(binding.switchSupperBannerAutoPlay.isChecked)
            setShowImageInfo(binding.switchSupperBannerInfo.isChecked)
            setShowIndicator(binding.switchSupperBannerIndicator.isChecked)
            setTransitionTimeMillis(
                binding.etSupperBannerTransition.text.toString().toLongOrNull()
                    ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
            )
            setOnBannerClickListener { item, _ ->
                startActivity(BannerDetailsActivity.createIntent(this@DevelopSettingsActivity, item))
            }
            setIndicatorCustomizer { indicator, selected, _ ->
                indicator.typeface = Typeface.MONOSPACE
                indicator.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                indicator.setTextColor(
                    if (selected) Color.parseColor("#061317") else Color.parseColor("#B8C9E8")
                )
                indicator.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (selected) Color.parseColor("#4EA1FF") else Color.parseColor("#2E3A4C"))
                    setStroke(2, Color.parseColor(if (selected) "#D8F0FF" else "#617089"))
                }
            }
        }

        binding.switchSupperBannerAutoPlay.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setAutoPlayEnabled(enabled)
        }
        binding.switchSupperBannerInfo.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setShowImageInfo(enabled)
        }
        binding.switchSupperBannerIndicator.setOnCheckedChangeListener { _, enabled ->
            binding.supperBanner.setShowIndicator(enabled)
        }
        binding.etSupperBannerTransition.doAfterTextChanged { text ->
            val transitionTime = text.toString().toLongOrNull() ?: return@doAfterTextChanged
            val coercedTime = SupperBannerConfig.coerceTransitionTimeMillis(transitionTime)
            binding.supperBanner.setTransitionTimeMillis(coercedTime)
        }
        binding.etSupperBannerTransition.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val coercedTime = SupperBannerConfig.coerceTransitionTimeMillis(
                    binding.etSupperBannerTransition.text.toString().toLongOrNull()
                        ?: SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
                )
                binding.etSupperBannerTransition.setText(coercedTime.toString())
                Toast.makeText(
                    this,
                    getString(R.string.develop_settings_supper_banner_transition_applied, coercedTime),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupInvincibleMode() {
        val isInvincible = viewModel.isInvincibleModeEnabled()
        GameStateManager.isInvincible = isInvincible
        binding.switchInvincible.isChecked = isInvincible
        updateInvincibleUi(isInvincible)

        binding.switchInvincible.setOnCheckedChangeListener { _, enabled ->
            viewModel.setInvincibleModeEnabled(enabled)
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
