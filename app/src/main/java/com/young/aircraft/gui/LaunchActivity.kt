package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityLaunchBinding
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {
    lateinit var binding: ActivityLaunchBinding
    private val db by lazy { DatabaseProvider.getDatabase(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val jetPlanes = Aircraft.JET_PLANES
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
        val playerId = settingsRepository.getOrCreateInstallId()
        val jetResId = jetPlanes[selectedJetIndex]
        lifecycleScope.launch {
            val savedData = db.playerGameDataDao().getByPlayerId(playerId)
            if (savedData.isNotEmpty() && savedData[0].level > 1) {
                val data = savedData[0]
                val savedLevel = data.level
                
                // Prioritize index, fallback to finding index from old resource ID, then default to 0
                val savedJetIndex = if (data.jetPlaneIndex in Aircraft.JET_PLANES.indices) {
                    data.jetPlaneIndex
                } else {
                    val foundIndex = Aircraft.JET_PLANES.indexOf(data.jetPlaneRes)
                    if (foundIndex != -1) foundIndex else 0
                }
                
                val savedJetRes = Aircraft.JET_PLANES[savedJetIndex]
                
                runOnUiThread {
                    val dialogView = LayoutInflater.from(this@LaunchActivity)
                        .inflate(R.layout.dialog_game, null)
                    dialogView.findViewById<TextView>(R.id.dialog_title).text = getString(R.string.continue_game_title)
                    dialogView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.continue_game_message, savedLevel)
                    val dialog = AlertDialog.Builder(this@LaunchActivity)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create()
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialogView.findViewById<TextView>(R.id.dialog_positive_btn).apply {
                        text = getString(R.string.continue_game_continue)
                        setOnClickListener {
                            dialog.dismiss()
                            startActivity(
                                Intent(this@LaunchActivity, MainActivity::class.java)
                                    .putExtra("start_level", savedLevel)
                                    .putExtra("jet_plane_res", savedJetRes)
                                    .putExtra("jet_plane_index", savedJetIndex)
                            )
                        }
                    }
                    dialogView.findViewById<TextView>(R.id.dialog_negative_btn).apply {
                        text = getString(R.string.continue_game_new)
                        visibility = View.VISIBLE
                        setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                db.playerGameDataDao().deleteByPlayerId(playerId)
                            }
                            startActivity(
                                Intent(this@LaunchActivity, MainActivity::class.java)
                                    .putExtra("jet_plane_res", jetResId)
                                    .putExtra("jet_plane_index", selectedJetIndex)
                            )
                        }
                    }
                    dialog.show()
                }
            } else {
                startActivity(
                    Intent(this@LaunchActivity, MainActivity::class.java)
                        .putExtra("jet_plane_res", jetResId)
                        .putExtra("jet_plane_index", selectedJetIndex)
                )
            }
        }
    }
}
