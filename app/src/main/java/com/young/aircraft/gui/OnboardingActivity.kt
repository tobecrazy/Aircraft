package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.young.aircraft.databinding.ActivityOnboardingBinding
import com.young.aircraft.providers.SettingsRepository

/**
 * 2-screen onboarding carousel — controls tutorial + power-ups overview.
 *
 * GATE: check onboarding_completed → skip to LaunchActivity if done / show carousel if not
 * Skip or Launch → save pref → LaunchActivity
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        settingsRepository = SettingsRepository(this)

        // Gate: skip if already completed
        if (settingsRepository.isOnboardingCompleted()) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Star field animation
        binding.starField.startAnimation()

        // ViewPager2 setup
        binding.viewPager.adapter = OnboardingPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = true

        // Page indicators
        updateIndicators(0)
        binding.viewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicators(position)
                    // Show LAUNCH on last page, NEXT on first
                    if (position == 1) {
                        binding.btnNext.text = getString(com.young.aircraft.R.string.onboarding_launch)
                    } else {
                        binding.btnNext.text = getString(com.young.aircraft.R.string.onboarding_next)
                    }
                }
            }
        )

        // SKIP button
        binding.btnSkip.setOnClickListener {
            completeOnboarding()
        }

        // NEXT / LAUNCH button
        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < 1) {
                binding.viewPager.currentItem = 1
            } else {
                completeOnboarding()
            }
        }
    }

    private fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
        startActivity(Intent(this, LaunchActivity::class.java))
        finish()
    }

    private fun updateIndicators(position: Int) {
        binding.indicator1.alpha = if (position == 0) 1.0f else 0.3f
        binding.indicator2.alpha = if (position == 1) 1.0f else 0.3f
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.starField.stopAnimation()
        }
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (::binding.isInitialized) {
            binding.starField.onUserActivity()
        }
        return super.dispatchTouchEvent(ev)
    }

    private class OnboardingPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OnboardingControlsFragment()
                else -> OnboardingPowerupsFragment()
            }
        }
    }
}
