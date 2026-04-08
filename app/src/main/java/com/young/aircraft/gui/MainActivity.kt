package com.young.aircraft.gui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.GameState
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.service.MusicService
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.utils.HallOfHeroesNameUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch


/**
 * @author Young
 */
class MainActivity : AppCompatActivity() {
    private enum class DialogTone {
        Success,
        Danger
    }

    private lateinit var mService: MusicService
    private lateinit var coreView: GameCoreView
    private var exitTime: Long = 0
    private var isExitInProgress = false
    private lateinit var playerId: String
    private var isServiceBound = false
    private val db by lazy { DatabaseProvider.getDatabase(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            mService = binder.getService()
            isServiceBound = true
            coreView.musicService = mService
            mService.backgroundSoundPlay()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            isServiceBound = false
            coreView.musicService = null
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        playerId = settingsRepository.getOrCreateInstallId()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitApp()
            }
        })
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
                badgeText = getString(R.string.game_over_badge),
                tone = DialogTone.Danger,
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
                badgeText = getString(R.string.level_complete_badge),
                tone = DialogTone.Success,
                title = getString(R.string.level_complete, completedLevel),
                message = getString(R.string.level_complete_message, completedLevel),
                positiveText = getString(R.string.next_level),
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.enemiesDestroyedThisLevel.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = (coreView.totalKills.toLong() * 100).toString(),
                onPositive = {
                    lifecycleScope.launch {
                        saveGameData(coreView, levelOverride = completedLevel + 1)
                        coreView.advanceToNextLevel()
                    }
                }
            )
        }
        coreView.onGameWon = {
            showHallOfHeroesBottomSheet()
        }
        val controller = window.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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

    private fun showGameDialog(
        badgeText: String? = null,
        tone: DialogTone = DialogTone.Success,
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
        dialogView.findViewById<TextView>(R.id.dialog_badge).apply {
            if (badgeText.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = badgeText
                setBackgroundResource(
                    when (tone) {
                        DialogTone.Danger -> R.drawable.dialog_badge_danger_bg
                        DialogTone.Success -> R.drawable.dialog_badge_positive_bg
                    }
                )
            }
        }
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

    private fun showHallOfHeroesBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_Aircraft_HallOfHeroesBottomSheet)
        val dialogView = dialog.layoutInflater.inflate(R.layout.bottom_sheet_hall_of_heroes, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.edit_hero_name)
        dialogView.findViewById<TextView>(R.id.text_hall_of_heroes_hint).text =
            getString(R.string.hall_of_heroes_hint)

        fun recordHero() {
            if (!dialog.isShowing) return
            val heroName = HallOfHeroesNameUtils.resolveSubmittedName(
                nameInput.text,
                getString(R.string.hall_of_heroes_anonymous)
            )
            dialog.dismiss()
            lifecycleScope.launch {
                saveGameData(coreView, heroName)
                finish()
            }
        }

        dialog.setContentView(dialogView)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialogView.findViewById<TextView>(R.id.button_record_hero).setOnClickListener {
            recordHero()
        }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                recordHero()
                true
            } else {
                false
            }
        }

        dialog.show()
        nameInput.requestFocus()
    }

    private suspend fun saveGameData(
        coreView: GameCoreView,
        playerName: String? = null,
        levelOverride: Int? = null
    ) {
        val score = coreView.totalKills.toLong() * 100
        val savedLevel = levelOverride ?: coreView.level
        val difficulty = settingsRepository.getDifficulty().persistedValue
        val existingRecord = db.playerGameDataDao().getByPlayerId(playerId).firstOrNull()
        val persistedPlayerName = playerName ?: existingRecord?.playerName
        db.playerGameDataDao().deleteByPlayerId(playerId)
        db.playerGameDataDao().insert(
            PlayerGameData(
                playerId = playerId,
                playerName = persistedPlayerName,
                level = savedLevel,
                score = score,
                jetPlaneRes = coreView.jetPlaneResId,
                jetPlaneIndex = coreView.jetPlaneIndex,
                difficulty = difficulty
            )
        )
        Log.d(
            "Game",
            "Saved: player=$playerId, name=$persistedPlayerName, level=$savedLevel, score=$score, jetIndex=${coreView.jetPlaneIndex}"
        )
    }

    private fun exitApp() {
        if ((System.currentTimeMillis() - exitTime) > 2000) {
            Toast.makeText(
                this, getString(R.string.exit_warning_msg),
                Toast.LENGTH_SHORT
            ).show()
            exitTime = System.currentTimeMillis()
        } else {
            if (isExitInProgress) return
            isExitInProgress = true
            coreView.pauseGame()
            lifecycleScope.launch {
                runCatching {
                    if (shouldAutoSaveOnExit()) {
                        saveGameData(coreView)
                    }
                }.onFailure {
                    Log.e("MainActivity", "Failed to auto-save progress on exit", it)
                }
                finish()
            }
        }
    }

    private fun shouldAutoSaveOnExit(): Boolean {
        return coreView.level > 1 || coreView.totalKills > 0
    }

    override fun onStart() {
        super.onStart()
        if (!isServiceBound) {
            Intent(this, MusicService::class.java).also { intent ->
                bindService(intent, connection, BIND_AUTO_CREATE)
            }
        }
    }


    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            mService.backgroundSoundStop()
            unbindService(connection)
            isServiceBound = false
        }
        coreView.musicService = null
    }

}
