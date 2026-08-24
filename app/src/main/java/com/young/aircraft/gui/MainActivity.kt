package com.young.aircraft.gui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
import com.young.aircraft.service.MusicService
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.GameCoreView
import com.young.aircraft.utils.HallOfHeroesNameUtils
import com.young.aircraft.gui.dialogs.DangerPalette
import com.young.aircraft.gui.dialogs.GameDialogPalette
import com.young.aircraft.gui.dialogs.GameDialogStat
import com.young.aircraft.gui.dialogs.GameDialogContent
import com.young.aircraft.gui.dialogs.SuccessPalette
import com.young.aircraft.gui.dialogs.setDialogComposeContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.viewmodel.GameViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch


/**
 * @author Young
 */
class MainActivity : AppCompatActivity() {
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
                tone = DangerPalette,
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
                tone = SuccessPalette,
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
        tone: GameDialogPalette,
        title: String,
        message: String,
        positiveText: String,
        primaryStatValue: Int,
        secondaryStatValue: Long,
        onPositive: () -> Unit,
        negativeText: String? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setDimAmount(0.7f)

        dialog.setDialogComposeContent(this) {
            GameDialogContent(
                badgeText = badgeText,
                palette = tone,
                title = title,
                message = message,
                primaryStat = GameDialogStat(
                    label = getString(R.string.stat_kills).let { "\u2694 $it" },
                    value = primaryStatValue.toString(),
                    countUp = primaryStatValue > 0
                ),
                secondaryStat = GameDialogStat(
                    label = getString(R.string.stat_score).let { "\u2605 $it" },
                    value = secondaryStatValue.toString(),
                    countUp = secondaryStatValue > 0
                ),
                positiveText = positiveText,
                onPositive = {
                    dialog.dismiss()
                    onPositive()
                },
                negativeText = negativeText,
                onNegative = onNegative?.let { callback -> { dialog.dismiss(); callback() } }
            )
        }
    }

    private fun showHallOfHeroesBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_Aircraft_HallOfHeroesBottomSheet)

        fun recordHero(rawName: String) {
            if (!dialog.isShowing) return
            val heroName = HallOfHeroesNameUtils.resolveSubmittedName(
                rawName.ifBlank { null },
                getString(R.string.hall_of_heroes_anonymous)
            )
            dialog.dismiss()
            lifecycleScope.launch {
                saveCurrentProgress(level = coreView.level, playerName = heroName)
                finish()
            }
        }

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        dialog.setDialogComposeContent(this) {
            HallOfHeroesContent(
                hint = getString(R.string.hall_of_heroes_hint),
                onRecord = ::recordHero
            )
        }

        // Entrance animation on the Material sheet frame (was the inflated content root).
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            alpha = 0f
            translationY = 120f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
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

// ── Hall of Heroes bottom-sheet content (Compose in a BottomSheetDialog shell) ──

private val SheetGradient = Brush.verticalGradient(listOf(Color(0xFF1A231C), Color(0xFF141A16)))
private val SheetBorder = Color(0x4438E08D)
private val SheetTopShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
private val SheetHandle = Color(0x4400FF88)
private val MedalBadgeContainer = Color(0x2900FF88)
private val MedalBadgeBorder = Color(0x6600FF88)
private val MedalBadgeText = Color(0xFFFFD9FFEC)
private val SheetTitleColor = Color(0xFF7DFFBB)
private val SheetMessageColor = Color(0xE6FFFFFF)
private val SheetPromptColor = Color(0xFF997DFFBB)
private val InputBackground = Color(0xFF101713)
private val InputBorder = Color(0x6600FF88)
private val InputHintColor = Color(0x66FFFFFF)
private val RecordButtonContainer = Color(0xCCDBFFEF)
private val RecordButtonText = Color(0xFF13221E)

@Composable
private fun HallOfHeroesContent(hint: String, onRecord: (String) -> Unit) {
    var heroName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SheetGradient, SheetTopShape)
            .border(1.dp, SheetBorder, SheetTopShape)
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 5.dp)
                .background(SheetHandle, RoundedCornerShape(999.dp))
        )

        Text(
            text = stringResource(R.string.hall_of_heroes_badge),
            color = MedalBadgeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 18.dp)
                .background(MedalBadgeContainer, RoundedCornerShape(999.dp))
                .border(1.dp, MedalBadgeBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Image(
            painter = painterResource(R.drawable.ic_hall_of_heroes_medal),
            contentDescription = stringResource(R.string.hall_of_heroes_medal_content_description),
            modifier = Modifier
                .padding(top = 18.dp)
                .size(152.dp)
        )

        Text(
            text = stringResource(R.string.hall_of_heroes_title),
            color = SheetTitleColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(R.string.hall_of_heroes_message),
            color = SheetMessageColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.hall_of_heroes_prompt),
                color = SheetPromptColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(50.dp)
                    .background(InputBackground, RoundedCornerShape(18.dp))
                    .border(1.dp, InputBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = heroName,
                    onValueChange = { heroName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onRecord(heroName) }),
                    cursorBrush = SolidColor(Color(0xFF00FF88)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (heroName.isEmpty()) {
                            Text(
                                text = stringResource(R.string.hall_of_heroes_name_hint),
                                color = InputHintColor,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                )
            }

            Text(
                text = hint,
                color = Color(0x88FFFFFF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .height(50.dp)
                .background(RecordButtonContainer, RoundedCornerShape(12.dp))
                .clickable { onRecord(heroName) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.hall_of_heroes_record_button),
                color = RecordButtonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
