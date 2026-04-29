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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.service.MusicService
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.utils.HallOfHeroesNameUtils
import com.young.aircraft.viewmodel.GameViewModel
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
    private lateinit var binding: ActivityMainBinding
    private lateinit var coreView: GameCoreView
    private lateinit var viewModel: GameViewModel
    private var exitTime: Long = 0
    private var isExitInProgress = false
    private var isServiceBound = false
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

        val playerId = settingsRepository.getOrCreateInstallId()
        val dao = DatabaseProvider.getDatabase(this).playerGameDataDao()
        viewModel = ViewModelProvider(this, GameViewModel.Factory(dao, playerId))[GameViewModel::class.java]

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
        val startLevel = intent.getIntExtra("start_level", 1)
        val jetPlaneRes = intent.getIntExtra("jet_plane_res", R.drawable.jet_plane_2)
        val jetPlaneIndex = intent.getIntExtra("jet_plane_index", 0)
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
        coreView.onGameOver = {
            val score = viewModel.calculateScore(coreView.totalKills)
            showGameDialog(
                badgeText = getString(R.string.game_over_badge),
                tone = DialogTone.Danger,
                title = getString(R.string.game_over_title),
                message = getString(R.string.game_over_message, coreView.level, score),
                titleColor = 0xFFFF4444.toInt(),
                positiveText = getString(R.string.game_over_save),
                negativeText = getString(R.string.game_over_discard),
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.totalKills.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = score.toString(),
                onPositive = {
                    lifecycleScope.launch {
                        viewModel.saveGameData(
                            level = coreView.level,
                            totalKills = coreView.totalKills,
                            jetPlaneResId = coreView.jetPlaneResId,
                            jetPlaneIndex = coreView.jetPlaneIndex,
                            difficulty = settingsRepository.getDifficulty().persistedValue
                        )
                        finish()
                    }
                },
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
                stat1Label = getString(R.string.stat_kills),
                stat1Value = coreView.enemiesDestroyedThisLevel.toString(),
                stat2Label = getString(R.string.stat_score),
                stat2Value = score.toString(),
                onPositive = {
                    lifecycleScope.launch {
                        viewModel.saveGameData(
                            level = completedLevel + 1,
                            totalKills = coreView.totalKills,
                            jetPlaneResId = coreView.jetPlaneResId,
                            jetPlaneIndex = coreView.jetPlaneIndex,
                            difficulty = settingsRepository.getDifficulty().persistedValue
                        )
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
        val difficultyLabel = when (settingsRepository.getDifficulty()) {
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
        lifecycleScope.launch {
            runCatching {
                if (viewModel.shouldAutoSaveOnExit(coreView.level, coreView.totalKills)) {
                    viewModel.saveGameData(
                        level = coreView.level,
                        totalKills = coreView.totalKills,
                        jetPlaneResId = coreView.jetPlaneResId,
                        jetPlaneIndex = coreView.jetPlaneIndex,
                        difficulty = settingsRepository.getDifficulty().persistedValue
                    )
                }
            }.onFailure {
                Log.e("MainActivity", "Failed to save progress from pause overlay", it)
            }
            finish()
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
            dialogView.findViewById<TextView>(R.id.stat_label_1).text = "⚔ $stat1Label"
            dialogView.findViewById<TextView>(R.id.stat_value_1).text = stat1Value
            dialogView.findViewById<TextView>(R.id.stat_label_2).text = "★ $stat2Label"
            dialogView.findViewById<TextView>(R.id.stat_value_2).text = stat2Value
        }

        val dividerColor = when (tone) {
            DialogTone.Danger -> 0x44FF4444.toInt()
            DialogTone.Success -> 0x4400FF88.toInt()
        }
        dialogView.findViewById<View>(R.id.dialog_divider).setBackgroundColor(dividerColor)

        val statCardBg = when (tone) {
            DialogTone.Danger -> R.drawable.dialog_stat_card_danger_bg
            DialogTone.Success -> R.drawable.dialog_stat_card_bg
        }
        dialogView.findViewById<LinearLayout>(R.id.stat_card_1).setBackgroundResource(statCardBg)
        dialogView.findViewById<LinearLayout>(R.id.stat_card_2).setBackgroundResource(statCardBg)

        val statLabelColor = when (tone) {
            DialogTone.Danger -> 0x88FF6F7E.toInt()
            DialogTone.Success -> 0x88FFFFFF.toInt()
        }
        dialogView.findViewById<TextView>(R.id.stat_label_1).setTextColor(statLabelColor)
        dialogView.findViewById<TextView>(R.id.stat_label_2).setTextColor(statLabelColor)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)
        val buttonBg = when (tone) {
            DialogTone.Danger -> R.drawable.dialog_button_primary_danger
            DialogTone.Success -> R.drawable.dialog_button_primary
        }
        dialogView.findViewById<TextView>(R.id.dialog_positive_btn).apply {
            text = positiveText
            setBackgroundResource(buttonBg)
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

        if (stat1Value != null) {
            val target1 = stat1Value.toIntOrNull() ?: 0
            if (target1 > 0) animateCountUp(dialogView.findViewById(R.id.stat_value_1), target1)
        }
        if (stat2Value != null) {
            val target2 = stat2Value.toIntOrNull() ?: 0
            if (target2 > 0) animateCountUp(dialogView.findViewById(R.id.stat_value_2), target2, 1000)
        }
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
                viewModel.saveGameData(
                    level = coreView.level,
                    totalKills = coreView.totalKills,
                    jetPlaneResId = coreView.jetPlaneResId,
                    jetPlaneIndex = coreView.jetPlaneIndex,
                    difficulty = settingsRepository.getDifficulty().persistedValue,
                    playerName = heroName
                )
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
        dialogView.alpha = 0f
        dialogView.translationY = 120f
        dialogView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
        nameInput.requestFocus()
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
                    if (viewModel.shouldAutoSaveOnExit(coreView.level, coreView.totalKills)) {
                        viewModel.saveGameData(
                            level = coreView.level,
                            totalKills = coreView.totalKills,
                            jetPlaneResId = coreView.jetPlaneResId,
                            jetPlaneIndex = coreView.jetPlaneIndex,
                            difficulty = settingsRepository.getDifficulty().persistedValue
                        )
                    }
                }.onFailure {
                    Log.e("MainActivity", "Failed to auto-save progress on exit", it)
                }
                finish()
            }
        }
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
