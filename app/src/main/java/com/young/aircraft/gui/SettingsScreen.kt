package com.young.aircraft.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.viewmodel.SettingsUiState

/** Screens reachable from the settings list (clear-cache is an action, not a destination). */
enum class SettingsDestination {
    DEVICE_INFO,
    QR_CODE_TOOL,
    FLASHLIGHT,
    PUZZLE,
    ABOUT_AIRCRAFT,
    ABOUT_ME,
    PRIVACY_POLICY,
    DEVELOP_SETTINGS
}

// Visuals lifted from settings_* / difficulty_* / badge_* / switch_* drawables (XML→Compose migration).
private val ScreenBg = Color(0xFF0F1118)
private val HeaderBg = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val DividerGreen = Color(0x4400FF88)
private val HeroGradient = Brush.linearGradient(listOf(Color(0x2E162033), Color(0x1F15242F)))
private val HeroBorder = Color(0x3300FF88)
private val TileBg = Color(0x22252A3A)
private val TileBorder = Color(0x2200FF88)
private val TilePressedBg = Color(0xFF2A2E44)
private val TilePressedBorder = Color(0x4400FF88)
private val ChipBg = Color(0x18FFFFFF)
private val ChipBorder = Color(0x28FFFFFF)
private val ChipActiveBg = Color(0x2600FF88)
private val ChipActiveBorder = Color(0x6600FF88)
private val ChipText = Color(0xFFD8E0EF)
private val OptionBg = Color(0x10FFFFFF)
private val OptionBorder = Color(0x22FFFFFF)
private val OptionSelectedBg = Color(0x2200FF88)
private val OptionSelectedBorder = Color(0xAA00FF88)
private val TitleWhite = Color(0xFFFFFFFF)
private val SummaryColor = Color(0xFFAAB4C8)
private val BannerSummaryColor = Color(0xFFCDD2E0)
private val SectionLabelColor = Color(0x66FFFFFF)
private val ActiveLabelColor = Color(0x88FFFFFF)
private val DescColor = Color(0x75FFFFFF)
private val EasyColor = Color(0xFF00FF88)
private val NormalColor = Color(0xFFFFFF00)
private val HardColor = Color(0xFFFF4444)
private val DangerText = Color(0xFFFF808D)
// SwitchCompat's default track tint renders white in this theme regardless of the drawable's
// checked color — replicate the rendered result (white track, thumb swaps white→green).
private val SwitchTrack = Color(0xFFFFFFFF)
private val SwitchThumbChecked = Color(0xFF00FF88)
private val SwitchThumbUnchecked = Color(0x88FFFFFF)

private val Mono = FontFamily.Monospace
private val ChipShape = RoundedCornerShape(50)
private val TileShape = RoundedCornerShape(16.dp)
private val OptionShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(12.dp)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    soundOptionCount: Int,
    onBack: () -> Unit,
    onDifficultySelected: (GameDifficulty) -> Unit,
    onBgSoundToggled: (Boolean) -> Unit,
    onCombatSoundToggled: (Boolean) -> Unit,
    onHitShakeToggled: (Boolean) -> Unit,
    onBgmFormatSelected: (String) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        SettingsHeader(onBack = onBack)
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
                HeroCard(state, soundOptionCount)
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
                            text = stringResource(R.string.settings_sound_active_count, state.enabledSoundCount, soundOptionCount),
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
                SectionLabel(
                    label = stringResource(R.string.settings_support_header),
                    topMargin = 22,
                    startContent = {
                        SmallChip(text = stringResource(R.string.other_settings_header))
                    }
                )
                NavRow(
                    title = stringResource(R.string.device_info_title),
                    summary = stringResource(R.string.device_info_summary),
                    topMargin = 10,
                    onClick = { onNavigate(SettingsDestination.DEVICE_INFO) }
                )
                NavRow(
                    title = stringResource(R.string.qr_code_tool_title),
                    summary = stringResource(R.string.qr_code_tool_summary),
                    onClick = { onNavigate(SettingsDestination.QR_CODE_TOOL) }
                )
                NavRow(
                    title = stringResource(R.string.flashlight_title),
                    summary = stringResource(R.string.flashlight_summary),
                    onClick = { onNavigate(SettingsDestination.FLASHLIGHT) }
                )
                NavRow(
                    title = stringResource(R.string.puzzle_game_title),
                    summary = stringResource(R.string.puzzle_game_summary),
                    onClick = { onNavigate(SettingsDestination.PUZZLE) }
                )
                NavRow(
                    title = stringResource(R.string.clear_cache_title),
                    summary = stringResource(R.string.clear_cache_summary),
                    onClick = onClearCache
                ) {
                    Text(
                        text = stringResource(R.string.clear_cache_chip),
                        color = DangerText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Mono,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .background(ChipBg, ChipShape)
                            .border(1.dp, ChipBorder, ChipShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (state.showDevelopSettings) {
                    NavRow(
                        title = stringResource(R.string.develop_settings_title),
                        summary = stringResource(R.string.develop_settings_summary),
                        onClick = { onNavigate(SettingsDestination.DEVELOP_SETTINGS) }
                    ) {
                        Text(
                            text = stringResource(R.string.develop_settings_debug_badge),
                            color = DangerText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Mono,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .background(ChipBg, ChipShape)
                                .border(1.dp, ChipBorder, ChipShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Chevron(Modifier.padding(start = 12.dp))
                    }
                }
                SectionLabel(
                    label = stringResource(R.string.settings_project_header),
                    topMargin = 22,
                    startContent = {
                        SmallChip(text = stringResource(R.string.about_us_title))
                    }
                )
                NavRow(
                    title = stringResource(R.string.about_aircraft_title),
                    summary = stringResource(R.string.about_aircraft_summary),
                    topMargin = 10,
                    onClick = { onNavigate(SettingsDestination.ABOUT_AIRCRAFT) }
                )
                NavRow(
                    title = stringResource(R.string.about_me_title),
                    summary = stringResource(R.string.about_me_summary),
                    onClick = { onNavigate(SettingsDestination.ABOUT_ME) }
                )
                NavRow(
                    title = stringResource(R.string.privacy_policy_title),
                    summary = stringResource(R.string.privacy_policy_summary),
                    bottomMargin = 20,
                    onClick = { onNavigate(SettingsDestination.PRIVACY_POLICY) }
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // <- push the whole header below the status bar
            .height(52.dp)
            .background(HeaderBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.title_activity_settings),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            letterSpacing = 0.25.sp
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(48.dp)
                .clickable(onClick = onBack, role = Role.Button),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
    }
}

@Composable
private fun HeroCard(state: SettingsUiState, soundOptionCount: Int) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(HeroGradient, RoundedCornerShape(18.dp))
            .border(1.dp, HeroBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        SmallChip(text = stringResource(R.string.title_activity_settings))
        Text(
            text = stringResource(R.string.settings_banner_title),
            color = TitleWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = stringResource(R.string.settings_banner_summary),
            color = BannerSummaryColor,
            fontSize = 13.sp,
            fontFamily = Mono,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            val difficultyLabel = when (state.difficulty) {
                GameDifficulty.EASY -> stringResource(R.string.difficulty_easy)
                GameDifficulty.HARD -> stringResource(R.string.difficulty_hard)
                else -> stringResource(R.string.difficulty_normal)
            }
            Text(
                text = stringResource(R.string.settings_profile_chip, difficultyLabel),
                color = TitleWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Mono,
                modifier = Modifier
                    .background(ChipActiveBg, ChipShape)
                    .border(1.dp, ChipActiveBorder, ChipShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            SoundCountChip(
                text = stringResource(R.string.settings_sound_profile_chip, state.enabledSoundCount, soundOptionCount),
                active = state.enabledSoundCount > 0,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun DifficultyCard(state: SettingsUiState, onSelected: (GameDifficulty) -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(TileBg, TileShape)
            .border(1.dp, TileBorder, TileShape)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_difficulty_card_title),
            color = TitleWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono
        )
        Text(
            text = stringResource(R.string.settings_difficulty_card_summary),
            color = SummaryColor,
            fontSize = 12.sp,
            fontFamily = Mono,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DifficultyOption(
                difficulty = GameDifficulty.EASY,
                selected = state.difficulty == GameDifficulty.EASY,
                badgeText = "1.2x",
                badgeColor = EasyColor,
                badgeBg = Color(0x3300FF88),
                descRes = R.string.difficulty_easy_desc,
                onClick = onSelected,
                modifier = Modifier.weight(1f)
            )
            DifficultyOption(
                difficulty = GameDifficulty.NORMAL,
                selected = state.difficulty == GameDifficulty.NORMAL,
                badgeText = "1.0x",
                badgeColor = NormalColor,
                badgeBg = Color(0x33FFFF00),
                descRes = R.string.difficulty_normal_desc,
                onClick = onSelected,
                modifier = Modifier.weight(1f)
            )
            DifficultyOption(
                difficulty = GameDifficulty.HARD,
                selected = state.difficulty == GameDifficulty.HARD,
                badgeText = "0.8x",
                badgeColor = HardColor,
                badgeBg = Color(0x33FF4444),
                descRes = R.string.difficulty_hard_desc,
                onClick = onSelected,
                modifier = Modifier.weight(1f)
            )
        }
        val (dotColor, labelRes) = when (state.difficulty) {
            GameDifficulty.EASY -> EasyColor to R.string.difficulty_easy
            GameDifficulty.HARD -> HardColor to R.string.difficulty_hard
            else -> NormalColor to R.string.difficulty_normal
        }
        Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Text(
                text = stringResource(R.string.difficulty_current, stringResource(labelRes)),
                color = ActiveLabelColor,
                fontSize = 11.sp,
                fontFamily = Mono,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    difficulty: GameDifficulty,
    selected: Boolean,
    badgeText: String,
    badgeColor: Color,
    badgeBg: Color,
    descRes: Int,
    onClick: (GameDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (selected) OptionSelectedBg else OptionBg, OptionShape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) OptionSelectedBorder else OptionBorder,
                shape = OptionShape
            )
            .clickable(role = Role.RadioButton) { onClick(difficulty) }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(8.dp).background(
                when (difficulty) {
                    GameDifficulty.EASY -> EasyColor
                    GameDifficulty.HARD -> HardColor
                    else -> NormalColor
                },
                CircleShape
            )
        )
        Text(
            text = stringResource(difficulty.labelRes()),
            color = TitleWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            modifier = Modifier
                .padding(top = 4.dp)
                .background(badgeBg, BadgeShape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            text = stringResource(descRes),
            color = DescColor,
            fontSize = 9.sp,
            fontFamily = Mono,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun GameDifficulty.labelRes(): Int = when (this) {
    GameDifficulty.EASY -> R.string.difficulty_easy
    GameDifficulty.HARD -> R.string.difficulty_hard
    else -> R.string.difficulty_normal
}

@Composable
private fun ToggleRow(
    title: String,
    status: String,
    topMargin: Int,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = topMargin.dp)
            .fillMaxWidth()
            .background(TileBg, TileShape)
            .border(1.dp, TileBorder, TileShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TitleWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Mono
            )
            Text(
                text = status,
                color = SummaryColor,
                fontSize = 11.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = stringResource(if (checked) R.string.settings_state_on else R.string.settings_state_off),
            color = TitleWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(if (checked) ChipActiveBg else ChipBg, ChipShape)
                .border(1.dp, if (checked) ChipActiveBorder else ChipBorder, ChipShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        SettingsSwitch(checked = checked, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .background(SwitchTrack, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (checked) 21.dp else 3.dp)
                .size(20.dp)
                .background(if (checked) SwitchThumbChecked else SwitchThumbUnchecked, CircleShape)
        )
    }
}

@Composable
private fun MusicFormatCard(state: SettingsUiState, onFormatSelected: (String) -> Unit) {
    val isOgg = state.bgmFormat == SettingsRepository.BGM_FORMAT_OGG
    val alpha = if (state.bgSoundEnabled) 1f else 0.5f
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(TileBg, TileShape)
            .border(1.dp, TileBorder, TileShape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bgm_format_title),
                    color = TitleWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono
                )
                Text(
                    text = stringResource(R.string.bgm_format_summary),
                    color = SummaryColor,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = stringResource(if (isOgg) R.string.bgm_format_ogg else R.string.bgm_format_mp3),
                color = TitleWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Mono,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(if (state.bgSoundEnabled) ChipActiveBg else ChipBg, ChipShape)
                    .border(1.dp, if (state.bgSoundEnabled) ChipActiveBorder else ChipBorder, ChipShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BgmFormatOption(
                text = stringResource(R.string.bgm_format_mp3),
                selected = !isOgg,
                enabled = state.bgSoundEnabled,
                onClick = { onFormatSelected(SettingsRepository.BGM_FORMAT_MP3) },
                modifier = Modifier.weight(1f)
            )
            BgmFormatOption(
                text = stringResource(R.string.bgm_format_ogg),
                selected = isOgg,
                enabled = state.bgSoundEnabled,
                onClick = { onFormatSelected(SettingsRepository.BGM_FORMAT_OGG) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BgmFormatOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = TitleWhite,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Mono,
        textAlign = TextAlign.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (selected) OptionSelectedBg else OptionBg, OptionShape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) OptionSelectedBorder else OptionBorder,
                shape = OptionShape
            )
            .clickable(enabled = enabled) { onClick(text) }
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
private fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    topMargin: Int = 12,
    bottomMargin: Int = 0,
    endContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .padding(top = topMargin.dp, bottom = bottomMargin.dp)
            .fillMaxWidth()
            .background(TileBg, TileShape)
            .border(1.dp, TileBorder, TileShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TitleWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Mono
            )
            Text(
                text = summary,
                color = SummaryColor,
                fontSize = 11.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (endContent != null) {
            endContent()
        } else {
            Chevron(Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    Text(
        text = "›",
        color = DividerGreen,
        fontSize = 22.sp,
        fontFamily = Mono,
        modifier = modifier
    )
}

@Composable
private fun SectionLabel(
    label: String,
    topMargin: Int,
    startContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .padding(top = topMargin.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(AccentGreen))
        if (startContent != null) {
            Box(Modifier.padding(start = 8.dp)) { startContent() }
        }
        Text(
            text = label,
            color = SectionLabelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        if (endContent != null) {
            endContent()
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Text(
        text = text,
        color = ChipText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Mono,
        modifier = Modifier
            .background(ChipBg, ChipShape)
            .border(1.dp, ChipBorder, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SoundCountChip(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TitleWhite,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Mono,
        modifier = modifier
            .background(if (active) ChipActiveBg else ChipBg, ChipShape)
            .border(1.dp, if (active) ChipActiveBorder else ChipBorder, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Preview(name = "Settings Screen", widthDp = 420, heightDp = 920, showBackground = true, backgroundColor = 0xFF0F1118)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        state = SettingsUiState(),
        soundOptionCount = 3,
        onBack = {},
        onDifficultySelected = {},
        onBgSoundToggled = {},
        onCombatSoundToggled = {},
        onHitShakeToggled = {},
        onBgmFormatSelected = {},
        onNavigate = {},
        onClearCache = {}
    )
}
