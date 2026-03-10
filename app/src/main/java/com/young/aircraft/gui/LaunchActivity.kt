package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityLaunchBinding
import com.young.aircraft.providers.DatabaseProvider
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {
    lateinit var binding: ActivityLaunchBinding
    private val db by lazy { DatabaseProvider.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.startGame.setOnClickListener {
            checkSavedGameAndStart()
        }
        binding.gameHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.gameSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkSavedGameAndStart() {
        val playerId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        lifecycleScope.launch {
            val savedData = db.playerGameDataDao().getByPlayerId(playerId)
            if (savedData.isNotEmpty() && savedData[0].level > 1) {
                val savedLevel = savedData[0].level
                runOnUiThread {
                    AlertDialog.Builder(this@LaunchActivity)
                        .setTitle(getString(R.string.continue_game_title))
                        .setMessage(getString(R.string.continue_game_message, savedLevel))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.continue_game_continue)) { dialog, _ ->
                            dialog.dismiss()
                            startActivity(
                                Intent(this@LaunchActivity, MainActivity::class.java)
                                    .putExtra("start_level", savedLevel)
                            )
                        }
                        .setNegativeButton(getString(R.string.continue_game_new)) { dialog, _ ->
                            dialog.dismiss()
                            lifecycleScope.launch {
                                db.playerGameDataDao().deleteByPlayerId(playerId)
                            }
                            startActivity(Intent(this@LaunchActivity, MainActivity::class.java))
                        }
                        .show()
                }
            } else {
                startActivity(Intent(this@LaunchActivity, MainActivity::class.java))
            }
        }
    }
}