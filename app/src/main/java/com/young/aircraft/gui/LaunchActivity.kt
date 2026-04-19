package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.gui.theme.AircraftTheme
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.launch

class LaunchActivity : ComponentActivity() {
    private val db by lazy { DatabaseProvider.getDatabase(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val jetPlanes = Aircraft.JET_PLANES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AircraftTheme {
                var showContinueDialog by remember { mutableStateOf<PlayerGameData?>(null) }
                var pendingJetIndex by remember { mutableIntStateOf(0) }

                LaunchScreen(
                    onStartGame = { jetIndex ->
                        pendingJetIndex = jetIndex
                        checkSavedGame { savedData ->
                            if (savedData != null && shouldOfferSavedGame(savedData)) {
                                showContinueDialog = savedData
                            } else {
                                startGame(jetIndex)
                            }
                        }
                    },
                    onHistoryClick = {
                        startActivity(Intent(this, HistoryActivity::class.java))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onStoreClick = {
                        startActivity(Intent(this, StoreActivity::class.java))
                    }
                )

                showContinueDialog?.let { data ->
                    AlertDialog(
                        onDismissRequest = { showContinueDialog = null },
                        title = { Text(getString(R.string.continue_game_title)) },
                        text = { Text(getString(R.string.continue_game_message, data.level)) },
                        confirmButton = {
                            Button(onClick = {
                                val savedJetIndex = if (data.jetPlaneIndex in Aircraft.JET_PLANES.indices) {
                                    data.jetPlaneIndex
                                } else {
                                    val foundIndex = Aircraft.JET_PLANES.indexOf(data.jetPlaneRes)
                                    if (foundIndex != -1) foundIndex else 0
                                }
                                val savedJetRes = Aircraft.JET_PLANES[savedJetIndex]
                                startActivity(
                                    Intent(this, MainActivity::class.java)
                                        .putExtra("start_level", data.level)
                                        .putExtra("jet_plane_res", savedJetRes)
                                        .putExtra("jet_plane_index", savedJetIndex)
                                )
                                showContinueDialog = null
                            }) {
                                Text(getString(R.string.continue_game_continue))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch {
                                    db.playerGameDataDao().deleteByPlayerId(settingsRepository.getOrCreateInstallId())
                                    startGame(pendingJetIndex)
                                }
                                showContinueDialog = null
                            }) {
                                Text(getString(R.string.continue_game_new))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkSavedGame(onResult: (PlayerGameData?) -> Unit) {
        val playerId = settingsRepository.getOrCreateInstallId()
        lifecycleScope.launch {
            val savedData = db.playerGameDataDao().getByPlayerId(playerId)
            onResult(savedData.firstOrNull())
        }
    }

    private fun startGame(jetIndex: Int) {
        val jetResId = jetPlanes[jetIndex]
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("jet_plane_res", jetResId)
                .putExtra("jet_plane_index", jetIndex)
        )
    }

    private fun shouldOfferSavedGame(data: PlayerGameData): Boolean {
        return data.level > 1 || data.score > 0L
    }
}
