package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.Secure
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.GameState
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.service.MusicService
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.viewmodel.MainActivityViewModel
import kotlinx.coroutines.launch


/**
 * @author Young
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var mService: MusicService
    private lateinit var coreView: GameCoreView
    private var exitTime: Long = 0
    private lateinit var playerId: String
    private val db by lazy { DatabaseProvider.getDatabase(this) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            mService = binder.getService()
            viewModel.updateSoundServiceStatus(true)
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            viewModel.updateSoundServiceStatus(false)
        }

    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        playerId = Secure.getString(contentResolver, Secure.ANDROID_ID)
        coreView = GameCoreView(this)
        val startLevel = intent.getIntExtra("start_level", 1)
        val jetPlaneRes = intent.getIntExtra("jet_plane_res", R.drawable.jet_plane_2)
        val jetPlaneIndex = intent.getIntExtra("jet_plane_index", 0)
        coreView.level = startLevel
        coreView.jetPlaneResId = jetPlaneRes
        coreView.jetPlaneIndex = jetPlaneIndex
        setContentView(coreView)
        coreView.onGameOver = {
            showGameDialog(
                title = getString(R.string.game_over_title),
                message = getString(R.string.game_over_message, coreView.level, coreView.totalKills.toLong() * 100),
                titleColor = 0xFFFF4444.toInt(),
                positiveText = getString(R.string.game_over_save),
                negativeText = getString(R.string.game_over_discard),
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.totalKills.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = (coreView.totalKills.toLong() * 100).toString(),
                onPositive = {
                    lifecycleScope.launch {
                        saveGameData(coreView)
                        finish()
                    }
                },
                onNegative = {
                    lifecycleScope.launch {
                        db.playerGameDataDao().deleteByPlayerId(playerId)
                        finish()
                    }
                }
            )
        }
        coreView.onLevelComplete = { completedLevel ->
            showGameDialog(
                title = getString(R.string.level_complete, completedLevel),
                message = getString(R.string.level_complete_message, completedLevel),
                positiveText = getString(R.string.next_level),
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.enemiesDestroyedThisLevel.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = (coreView.totalKills.toLong() * 100).toString(),
                onPositive = {
                    lifecycleScope.launch {
                        saveGameData(coreView)
                        coreView.advanceToNextLevel()
                    }
                }
            )
        }
        coreView.onGameWon = {
            showGameDialog(
                title = getString(R.string.game_won),
                message = null,
                titleColor = 0xFFFFD700.toInt(),
                positiveText = getString(R.string.dialog_ok),
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.totalKills.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = (coreView.totalKills.toLong() * 100).toString(),
                onPositive = {
                    lifecycleScope.launch {
                        saveGameData(coreView)
                        finish()
                    }
                }
            )
        }
        val controller = window.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        viewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(MainActivityViewModel::class.java)

        viewModel.isReadToPlaySound.observe(this, Observer {
            if (it) {
                Log.d("YoungTest", "===> to play background sound")
                coreView.musicService = mService
                Looper.myLooper()?.let { looper ->
                    Handler(looper).postDelayed({
                        mService.backgroundSoundPlay()
                    }, 200)
                }

            }
        })

        lifecycleScope.launch {
            GameStateManager.gameState.collect { state ->
                when (state) {
                    GameState.LOW_MEMORY -> {
                        coreView.pauseGame()
                        Log.d("MainActivity", "Game paused due to low memory")
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                exitApp()
                return false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showGameDialog(
        title: String,
        message: String?,
        titleColor: Int = 0xFF00FF88.toInt(),
        positiveText: String,
        negativeText: String? = null,
        stat1Label: String? = null,
        stat1Value: String? = null,
        stat2Label: String? = null,
        stat2Value: String? = null,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_game, null)
        dialogView.findViewById<TextView>(R.id.dialog_title).apply {
            text = title
            setTextColor(titleColor)
        }
        dialogView.findViewById<TextView>(R.id.dialog_message).apply {
            if (message != null) {
                text = message
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        if (stat1Label != null && stat2Label != null) {
            dialogView.findViewById<LinearLayout>(R.id.dialog_stats_container).visibility = View.VISIBLE
            dialogView.findViewById<TextView>(R.id.stat_label_1).text = stat1Label
            dialogView.findViewById<TextView>(R.id.stat_value_1).text = stat1Value
            dialogView.findViewById<TextView>(R.id.stat_label_2).text = stat2Label
            dialogView.findViewById<TextView>(R.id.stat_value_2).text = stat2Value
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.dialog_positive_btn).apply {
            text = positiveText
            setOnClickListener {
                dialog.dismiss()
                onPositive()
            }
        }
        if (negativeText != null && onNegative != null) {
            dialogView.findViewById<TextView>(R.id.dialog_negative_btn).apply {
                text = negativeText
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    onNegative()
                }
            }
        }
        dialog.show()
    }

    private suspend fun saveGameData(coreView: GameCoreView) {
        val score = coreView.totalKills.toLong() * 100
        val difficulty = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("difficulty", "1.0") ?: "1.0"
        db.playerGameDataDao().deleteByPlayerId(playerId)
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = playerId,
                level = coreView.level,
                score = score,
                jetPlaneRes = coreView.jetPlaneResId,
                jetPlaneIndex = coreView.jetPlaneIndex,
                difficulty = difficulty
            )
        )
        Log.d("Game", "Saved: player=$playerId, level=${coreView.level}, score=$score, jetIndex=${coreView.jetPlaneIndex}")
    }

    private fun exitApp() {
        if ((System.currentTimeMillis() - exitTime) > 2000) {
            Toast.makeText(
                this, getString(R.string.exit_warning_msg),
                Toast.LENGTH_SHORT
            ).show()
            exitTime = System.currentTimeMillis()
        } else {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }


    override fun onStop() {
        super.onStop()
        unbindService(connection)
        viewModel.updateSoundServiceStatus(false)
    }

}