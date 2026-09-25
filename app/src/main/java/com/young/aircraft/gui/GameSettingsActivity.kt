package com.young.aircraft.gui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.DividerGreen
import com.young.aircraft.viewmodel.SettingsUiState
import com.young.aircraft.viewmodel.SettingsViewModel

class GameSettingsActivity : AppCompatActivity() {
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        viewModel = ViewModelProvider(this, SettingsViewModel.Factory(this))[SettingsViewModel::class.java]

        setContent {
            AircraftTheme {
                val state by viewModel.uiState.collectAsState()
                GameSettingsScreen(
                    state = state,
                    onBack = { finish() },
                    onDifficultySelected = viewModel::setDifficulty,
                    onBgSoundToggled = viewModel::setBgSoundEnabled,
                    onCombatSoundToggled = viewModel::setCombatSoundEnabled,
                    onHitShakeToggled = viewModel::setHitShakeEnabled,
                    onBgmFormatSelected = viewModel::setBgmFormat
                )
            }
        }
    }
}

@Composable
fun GameSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDifficultySelected: (GameDifficulty) -> Unit,
    onBgSoundToggled: (Boolean) -> Unit,
    onCombatSoundToggled: (Boolean) -> Unit,
    onHitShakeToggled: (Boolean) -> Unit,
    onBgmFormatSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        SettingsHeader(title = stringResource(R.string.game_settings_title), onBack = onBack)
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGreen))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).padding(horizontal = 14.dp)
            ) {
                SectionLabel(
                    label = stringResource(R.string.difficulty_settings_header),
                    topMargin = 22
                )
                DifficultyCard(state, onDifficultySelected)
                SectionLabel(
                    label = stringResource(R.string.sound_settings_header),
                    topMargin = 22,
                    endContent = {
                        SoundCountChip(
                            text = stringResource(
                                R.string.settings_sound_active_count,
                                state.enabledSoundCount,
                                state.soundOptionCount
                            ),
                            active = state.enabledSoundCount > 0
                        )
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.background_sound_title),
                    status = stringResource(
                        if (state.bgSoundEnabled) R.string.background_sound_summary_on
                        else R.string.background_sound_summary_off
                    ),
                    topMargin = 10,
                    checked = state.bgSoundEnabled,
                    onToggle = onBgSoundToggled
                )
                MusicFormatCard(state, onBgmFormatSelected)
                ToggleRow(
                    title = stringResource(R.string.combat_sound_title),
                    status = stringResource(
                        if (state.combatSoundEnabled) R.string.combat_sound_summary_on
                        else R.string.combat_sound_summary_off
                    ),
                    topMargin = 12,
                    checked = state.combatSoundEnabled,
                    onToggle = onCombatSoundToggled
                )
                ToggleRow(
                    title = stringResource(R.string.hit_shake_effect_title),
                    status = stringResource(
                        if (state.hitShakeEnabled) R.string.hit_shake_effect_summary_on
                        else R.string.hit_shake_effect_summary_off
                    ),
                    topMargin = 12,
                    checked = state.hitShakeEnabled,
                    onToggle = onHitShakeToggled
                )
            }
        }
    }
}

@Preview(name = "Game Settings Screen", widthDp = 420, heightDp = 920, showBackground = true, backgroundColor = 0xFF0F1118)
@Composable
private fun GameSettingsScreenPreview() {
    GameSettingsScreen(
        state = SettingsUiState(),
        onBack = {},
        onDifficultySelected = {},
        onBgSoundToggled = {},
        onCombatSoundToggled = {},
        onHitShakeToggled = {},
        onBgmFormatSelected = {}
    )
}
