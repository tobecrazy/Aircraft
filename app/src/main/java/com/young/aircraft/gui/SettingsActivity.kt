package com.young.aircraft.gui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.young.aircraft.R
import com.young.aircraft.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {
    lateinit var binding: SettingsActivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val difficultyMap = linkedMapOf(
            "1.2" to "easy",
            "1.0" to "normal",
            "0.8" to "hard"
        )

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val difficultyPref = preferenceScreen.findPreference<Preference>("difficulty")
            difficultyPref?.let { pref ->
                updateDifficultySummary(pref)
                pref.setOnPreferenceClickListener {
                    showDifficultyDialog()
                    true
                }
            }

            val privacy =
                preferenceScreen.findPreference<Preference>(getString(R.string.privacy_policy_title))
            privacy?.setOnPreferenceClickListener {
                navigateToPrivacyPolicy()
                true
            }
            val deviceInfo = preferenceScreen.findPreference<Preference>("device_info")
            deviceInfo?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), DeviceInfoActivity::class.java))
                true
            }
            val aboutAircraft = preferenceScreen.findPreference<Preference>("about_aircraft")
            aboutAircraft?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AboutAircraftActivity::class.java))
                true
            }
        }

        private fun getCurrentDifficulty(): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            return prefs.getString("difficulty", "1.0") ?: "1.0"
        }

        private fun saveDifficulty(value: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString("difficulty", value).apply()
        }

        private fun getDifficultyLabel(value: String): String {
            return when (value) {
                "1.2" -> getString(R.string.difficulty_easy)
                "0.8" -> getString(R.string.difficulty_hard)
                else -> getString(R.string.difficulty_normal)
            }
        }

        private fun updateDifficultySummary(pref: Preference) {
            pref.summary = getDifficultyLabel(getCurrentDifficulty())
        }

        private fun showDifficultyDialog() {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_difficulty)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val optionEasy = dialog.findViewById<LinearLayout>(R.id.option_easy)
            val optionNormal = dialog.findViewById<LinearLayout>(R.id.option_normal)
            val optionHard = dialog.findViewById<LinearLayout>(R.id.option_hard)
            val currentDot = dialog.findViewById<View>(R.id.current_indicator_dot)
            val currentLabel = dialog.findViewById<TextView>(R.id.current_selection_label)

            val options = mapOf(
                "1.2" to optionEasy,
                "1.0" to optionNormal,
                "0.8" to optionHard
            )

            fun updateSelection(value: String) {
                options.forEach { (key, view) -> view.isSelected = key == value }
                currentLabel.text = getString(R.string.difficulty_current, getDifficultyLabel(value))
                val dotDrawable = when (value) {
                    "1.2" -> R.drawable.difficulty_indicator_easy
                    "0.8" -> R.drawable.difficulty_indicator_hard
                    else -> R.drawable.difficulty_indicator_normal
                }
                currentDot.setBackgroundResource(dotDrawable)
            }

            updateSelection(getCurrentDifficulty())

            fun onOptionClick(value: String) {
                saveDifficulty(value)
                updateSelection(value)
                val pref = preferenceScreen.findPreference<Preference>("difficulty")
                pref?.let { updateDifficultySummary(it) }
                dialog.dismiss()
            }

            optionEasy.setOnClickListener { onOptionClick("1.2") }
            optionNormal.setOnClickListener { onOptionClick("1.0") }
            optionHard.setOnClickListener { onOptionClick("0.8") }

            dialog.show()
        }

        private fun navigateToPrivacyPolicy() {
            val intent = Intent(requireContext(), PrivacyPolicyActivity::class.java)
            startActivity(intent)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        }
    }
}
