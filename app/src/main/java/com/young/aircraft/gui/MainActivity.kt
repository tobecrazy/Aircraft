package com.young.aircraft.gui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        playerId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val coreView = GameCoreView(this)
        val startLevel = intent.getIntExtra("start_level", 1)
        val jetPlaneRes = intent.getIntExtra("jet_plane_res", R.drawable.jet_plane)
        coreView.level = startLevel
        coreView.jetPlaneResId = jetPlaneRes
        setContentView(coreView)
        coreView.onGameOver = {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.game_over_title))
                .setMessage(getString(R.string.game_over_message, coreView.level, coreView.totalKills.toLong() * 100))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.game_over_save)) { dialog, _ ->
                    dialog.dismiss()
                    lifecycleScope.launch {
                        saveGameData(coreView)
                        finish()
                    }
                }
                .setNegativeButton(getString(R.string.game_over_discard)) { dialog, _ ->
                    dialog.dismiss()
                    lifecycleScope.launch {
                        db.playerGameDataDao().deleteByPlayerId(playerId)
                        finish()
                    }
                }
                .show()
        }
        coreView.onLevelComplete = { completedLevel ->
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.level_complete, completedLevel))
                .setMessage(getString(R.string.level_complete_message, completedLevel))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                    dialog.dismiss()
                    coreView.advanceToNextLevel()
                }
                .show()
        }
        coreView.onGameWon = {
            val nameInput = EditText(this).apply {
                hint = getString(R.string.game_won_name_prompt)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.game_won))
                .setView(nameInput)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.dialog_ok)) { dialog, _ ->
                    val playerName = nameInput.text.toString()
                    Log.d("Game", "Player name: $playerName")
                    dialog.dismiss()
                    lifecycleScope.launch {
                        saveGameData(coreView)
                        finish()
                    }
                }
                .show()
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

    private suspend fun saveGameData(coreView: GameCoreView) {
        val score = coreView.totalKills.toLong() * 100
        db.playerGameDataDao().deleteByPlayerId(playerId)
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = playerId,
                level = coreView.level,
                score = score,
                jetPlaneRes = coreView.jetPlaneResId
            )
        )
        Log.d("Game", "Saved: player=$playerId, level=${coreView.level}, score=$score")
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