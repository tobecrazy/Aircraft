package com.young.aircraft.gui

import android.animation.ValueAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameState
import com.young.aircraft.databinding.ActivityMainBinding
import com.young.aircraft.databinding.BottomSheetHallOfHeroesBinding
import com.young.aircraft.databinding.DialogGameBinding
import com.young.aircraft.service.MusicService
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.utils.HallOfHeroesNameUtils
import com.young.aircraft.viewmodel.GameViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch


/**
 * @author Young
 */
class MainActivity : AppCompatActivity() {
    private enum class DialogTone(
        val titleColor: Int,
        val dividerColor: Int,
        val badgeBackgroundRes: Int,
        val statCardBackgroundRes: Int,
        val statLabelColor: Int,
        val positiveButtonBackgroundRes: Int
    ) {
        Success(
            titleColor = 0xFF00FF88.toInt(),
            dividerColor = 0x4400FF88.toInt(),
            badgeBackgroundRes = R.drawable.dialog_badge_positive_bg,
            statCardBackgroundRes = R.drawable.dialog_stat_card_bg,
            statLabelColor = 0x88FFFFFF.toInt(),
            positiveButtonBackgroundRes = R.drawable.dialog_button_primary
        ),
        Danger(
            titleColor = 0xFFFF4444.toInt(),
            dividerColor = 0x44FF4444.toInt(),
            badgeBackgroundRes = R.drawable.dialog_badge_danger_bg,
            statCardBackgroundRes = R.drawable.dialog_stat_card_danger_bg,
            statLabelColor = 0x88FF6F7E.toInt(),
            positiveButtonBackgroundRes = R.drawable.dialog_button_primary_danger
        )
    }

    private lateinit var mService: MusicService
    private lateinit var binding: ActivityMainBinding
    private lateinit var coreView: GameCoreView
    private lateinit var viewModel: GameViewModel
    private var exitTime: Long = 0
    private var isExitInProgress = false
    private var isServiceBound = false

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

        viewModel = ViewModelProvider(this, GameViewModel.Factory(this))[GameViewModel::class.java]

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.pauseOverlay.isVisible) {
                    hidePauseOverlay()
                } else {
                    exitApp()
                }
            }
        })
        binding = ActivityMainBinding.inflate(layoutInflater)
        coreView = GameCoreView(this)
        val startLevel = intent.getIntExtra(AircraftConstants.IntentExtras.START_LEVEL, 1)
        val jetPlaneRes = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_RES, R.drawable.jet_plane_2)
        val jetPlaneIndex = intent.getIntExtra(AircraftConstants.IntentExtras.JET_PLANE_INDEX, 0)
        val startKills = intent.getIntExtra(AircraftConstants.IntentExtras.TOTAL_KILLS, 0)
        coreView.level = startLevel
        coreView.jetPlaneResId = jetPlaneRes
        coreView.jetPlaneIndex = jetPlaneIndex
        setContentView(binding.root)
        binding.gameContainer.addView(
            coreView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        configureOverlayUi(startLevel = startLevel, jetPlaneIndex = jetPlaneIndex)
        coreView.totalKills = startKills
        coreView.onGameOver = {
            val score = viewModel.calculateScore(coreView.totalKills)
            showGameDialog(
                badgeText = getString(R.string.game_over_badge),
                tone = DialogTone.Danger,
                title = getString(R.string.game_over_title),
                message = getString(R.string.game_over_message, coreView.level, score),
                positiveText = getString(R.string.game_over_save),
                primaryStatValue = coreView.totalKills,
                secondaryStatValue = score,
                onPositive = {
                    lifecycleScope.launch {
                        saveCurrentProgress(level = coreView.level)
                        finish()
                    }
                },
                negativeText = getString(R.string.game_over_discard),
                onNegative = {
                    lifecycleScope.launch {
                        viewModel.deletePlayerData()
                        finish()
                    }
                }
            )
        }
        coreView.onLevelComplete = { completedLevel ->
            val score = viewModel.calculateScore(coreView.totalKills)
            showGameDialog(
                badgeText = getString(R.string.level_complete_badge),
                tone = DialogTone.Success,
                title = getString(R.string.level_complete, completedLevel),
                message = getString(R.string.level_complete_message, completedLevel),
                positiveText = getString(R.string.next_level),
                primaryStatValue = coreView.enemiesDestroyedThisLevel,
                secondaryStatValue = score,
                onPositive = {
                    lifecycleScope.launch {
                        saveCurrentProgress(level = completedLevel + 1)
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
                        showPauseOverlay()
                        Log.d("MainActivity", "Game paused due to low memory")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun configureOverlayUi(startLevel: Int, jetPlaneIndex: Int) {
        bindMissionBriefing(startLevel, jetPlaneIndex)
        binding.btnPause.setOnClickListener {
            showPauseOverlay()
        }
        binding.btnResume.setOnClickListener {
            hidePauseOverlay()
        }
        binding.btnQuit.setOnClickListener {
            quitFromPauseOverlay()
        }
        binding.gameTipCard.postDelayed({
            if (!isFinishing && !isDestroyed && binding.gameTipCard.isVisible) {
                binding.gameTipCard.animate()
                    .alpha(0f)
                    .translationY(binding.gameTipCard.height / 3f)
                    .setDuration(280)
                    .withEndAction {
                        binding.gameTipCard.isVisible = false
                    }
                    .start()
            }
        }, 4200)
    }

    private fun bindMissionBriefing(startLevel: Int, jetPlaneIndex: Int) {
        val difficultyLabel = when (viewModel.getDifficulty()) {
            GameDifficulty.EASY -> getString(R.string.difficulty_easy)
            GameDifficulty.NORMAL -> getString(R.string.difficulty_normal)
            GameDifficulty.HARD -> getString(R.string.difficulty_hard)
        }
        binding.tvSectorChip.text = getString(R.string.game_hud_chip_sector, startLevel)
        binding.tvDifficultyChip.text = getString(R.string.game_hud_chip_difficulty, difficultyLabel)
        binding.tvAirframeChip.text = getString(R.string.game_hud_chip_airframe, jetPlaneIndex + 1)
    }

    private fun showPauseOverlay() {
        if (binding.pauseOverlay.isVisible) return
        coreView.pauseGame()
        binding.pauseOverlay.apply {
            alpha = 0f
            isVisible = true
            animate()
                .alpha(1f)
                .setDuration(180)
                .start()
        }
        binding.pausePanel.apply {
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hidePauseOverlay(shouldResumeGame: Boolean = true) {
        if (!binding.pauseOverlay.isVisible) return
        binding.pauseOverlay.animate()
            .alpha(0f)
            .setDuration(160)
            .withEndAction {
                binding.pauseOverlay.isVisible = false
            }
            .start()
        if (shouldResumeGame) {
            coreView.resumeGame()
        }
    }

    private fun quitFromPauseOverlay() {
        if (isExitInProgress) return
        isExitInProgress = true
        hidePauseOverlay(shouldResumeGame = false)
        autoSaveAndFinish("Failed to save progress from pause overlay")
    }

    private fun showGameDialog(
        badgeText: String,
        tone: DialogTone,
        title: String,
        message: String,
        positiveText: String,
        primaryStatValue: Int,
        secondaryStatValue: Long,
        onPositive: () -> Unit,
        negativeText: String? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val dialogBinding = DialogGameBinding.inflate(layoutInflater)
        dialogBinding.dialogBadge.apply {
            text = badgeText
            visibility = View.VISIBLE
            setBackgroundResource(tone.badgeBackgroundRes)
        }
        dialogBinding.dialogTitle.apply {
            text = title
            setTextColor(tone.titleColor)
        }
        dialogBinding.dialogMessage.text = message
        dialogBinding.dialogStatsContainer.visibility = View.VISIBLE
        dialogBinding.statLabel1.text = getString(R.string.stat_kills).let { "\u2694 $it" }
        dialogBinding.statValue1.text = primaryStatValue.toString()
        dialogBinding.statLabel2.text = getString(R.string.stat_score).let { "\u2605 $it" }
        dialogBinding.statValue2.text = secondaryStatValue.toString()
        dialogBinding.dialogDivider.setBackgroundColor(tone.dividerColor)
        dialogBinding.statCard1.setBackgroundResource(tone.statCardBackgroundRes)
        dialogBinding.statCard2.setBackgroundResource(tone.statCardBackgroundRes)
        dialogBinding.statLabel1.setTextColor(tone.statLabelColor)
        dialogBinding.statLabel2.setTextColor(tone.statLabelColor)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)
        dialogBinding.dialogPositiveBtn.apply {
            text = positiveText
            setBackgroundResource(tone.positiveButtonBackgroundRes)
            setOnClickListener {
                dialog.dismiss()
                onPositive()
            }
        }
        if (negativeText != null && onNegative != null) {
            dialogBinding.dialogNegativeBtn.apply {
                text = negativeText
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    onNegative()
                }
            }
        }
        dialog.show()

        if (primaryStatValue > 0) animateCountUp(dialogBinding.statValue1, primaryStatValue)
        if (secondaryStatValue > 0) animateCountUp(dialogBinding.statValue2, secondaryStatValue.toInt(), 1000)
    }

    private fun animateCountUp(textView: TextView, targetValue: Int, durationMs: Long = 800) {
        ValueAnimator.ofInt(0, targetValue).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 200
            addUpdateListener { textView.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    private fun showHallOfHeroesBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_Aircraft_HallOfHeroesBottomSheet)
        val sheetBinding = BottomSheetHallOfHeroesBinding.inflate(dialog.layoutInflater)
        sheetBinding.textHallOfHeroesHint.text = getString(R.string.hall_of_heroes_hint)

        fun recordHero() {
            if (!dialog.isShowing) return
            val heroName = HallOfHeroesNameUtils.resolveSubmittedName(
                sheetBinding.editHeroName.text,
                getString(R.string.hall_of_heroes_anonymous)
            )
            dialog.dismiss()
            lifecycleScope.launch {
                saveCurrentProgress(level = coreView.level, playerName = heroName)
                finish()
            }
        }

        dialog.setContentView(sheetBinding.root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        sheetBinding.buttonRecordHero.setOnClickListener {
            recordHero()
        }
        sheetBinding.editHeroName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                recordHero()
                true
            } else {
                false
            }
        }

        dialog.show()
        sheetBinding.root.alpha = 0f
        sheetBinding.root.translationY = 120f
        sheetBinding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
        sheetBinding.editHeroName.requestFocus()
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
            autoSaveAndFinish("Failed to auto-save progress on exit")
        }
    }

    private fun autoSaveAndFinish(logMessage: String) {
        lifecycleScope.launch {
            runCatching {
                if (viewModel.shouldAutoSaveOnExit(coreView.level, coreView.totalKills)) {
                    saveCurrentProgress(level = coreView.level)
                }
            }.onFailure {
                Log.e("MainActivity", logMessage, it)
            }
            finish()
        }
    }

    private suspend fun saveCurrentProgress(level: Int, playerName: String? = null) {
        viewModel.saveAirBattleData(
            level = level,
            totalKills = coreView.totalKills,
            jetPlaneResId = coreView.jetPlaneResId,
            jetPlaneIndex = coreView.jetPlaneIndex,
            playerName = playerName
        )
    }

    override fun onStart() {
        super.onStart()
        if (!isServiceBound) {
            Intent(this, MusicService::class.java).also { intent ->
                bindService(intent, connection, BIND_AUTO_CREATE)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }


    override fun onStop() {
        super.onStop()
        if (binding.pauseOverlay.isVisible) {
            binding.pauseOverlay.clearAnimation()
            binding.pauseOverlay.isVisible = false
        }
        if (isServiceBound) {
            mService.backgroundSoundStop()
            unbindService(connection)
            isServiceBound = false
        }
        coreView.musicService = null
    }

}
