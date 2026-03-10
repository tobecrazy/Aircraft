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
    private val jetPlanes = intArrayOf(R.drawable.jet_plane, R.drawable.jet_plane_1)
    private var selectedJetIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playerJetPlane.setOnClickListener {
            selectedJetIndex = (selectedJetIndex + 1) % jetPlanes.size
            binding.playerJetPlane.setImageResource(jetPlanes[selectedJetIndex])
        }
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
        val jetResId = jetPlanes[selectedJetIndex]
        lifecycleScope.launch {
            val savedData = db.playerGameDataDao().getByPlayerId(playerId)
            if (savedData.isNotEmpty() && savedData[0].level > 1) {
                val savedLevel = savedData[0].level
                val savedJetRes = savedData[0].jetPlaneRes.takeIf { it != 0 } ?: R.drawable.jet_plane
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
                                    .putExtra("jet_plane_res", savedJetRes)
                            )
                        }
                        .setNegativeButton(getString(R.string.continue_game_new)) { dialog, _ ->
                            dialog.dismiss()
                            lifecycleScope.launch {
                                db.playerGameDataDao().deleteByPlayerId(playerId)
                            }
                            startActivity(
                                Intent(this@LaunchActivity, MainActivity::class.java)
                                    .putExtra("jet_plane_res", jetResId)
                            )
                        }
                        .show()
                }
            } else {
                startActivity(
                    Intent(this@LaunchActivity, MainActivity::class.java)
                        .putExtra("jet_plane_res", jetResId)
                )
            }
        }
    }
}