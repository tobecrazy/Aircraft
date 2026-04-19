package com.young.aircraft.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    onNavigateToQRCode: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAboutMe: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToDevelop: () -> Unit,
    showDevelopSettings: Boolean
) {
    val difficulty by viewModel.difficulty.collectAsState()
    val backgroundSoundEnabled by viewModel.backgroundSoundEnabled.collectAsState()
    val combatSoundEnabled by viewModel.combatSoundEnabled.collectAsState()
    val hitShakeEffectEnabled by viewModel.hitShakeEffectEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle(text = stringResource(R.string.difficulty_settings_header))
                DifficultySelection(
                    selectedDifficulty = difficulty,
                    onDifficultySelected = { viewModel.setDifficulty(it) }
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.sound_settings_header))
                Card {
                    Column {
                        ToggleRow(
                            icon = Icons.Default.MusicNote,
                            title = stringResource(R.string.background_sound_title),
                            enabled = backgroundSoundEnabled,
                            onToggle = { viewModel.setBackgroundSoundEnabled(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = stringResource(R.string.combat_sound_title),
                            enabled = combatSoundEnabled,
                            onToggle = { viewModel.setCombatSoundEnabled(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ToggleRow(
                            icon = Icons.Default.Vibration,
                            title = stringResource(R.string.hit_shake_effect_title),
                            enabled = hitShakeEffectEnabled,
                            onToggle = { viewModel.setHitShakeEffectEnabled(it) }
                        )
                    }
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.other_settings_header))
                Card {
                    Column {
                        NavigationRow(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.device_info_title),
                            onClick = onNavigateToDeviceInfo
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NavigationRow(
                            icon = Icons.Default.QrCode,
                            title = stringResource(R.string.qr_code_tool_title),
                            onClick = onNavigateToQRCode
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NavigationRow(
                            icon = Icons.Default.Flight,
                            title = stringResource(R.string.about_aircraft_title),
                            onClick = onNavigateToAbout
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NavigationRow(
                            icon = Icons.Default.Person,
                            title = stringResource(R.string.about_me_title),
                            onClick = onNavigateToAboutMe
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NavigationRow(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(R.string.privacy_policy_title),
                            onClick = onNavigateToPrivacy
                        )
                    }
                }
            }

            if (showDevelopSettings) {
                item {
                    SectionTitle(text = stringResource(R.string.develop_settings_title))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        NavigationRow(
                            icon = Icons.Default.BugReport,
                            title = stringResource(R.string.develop_settings_title),
                            onClick = onNavigateToDevelop,
                            textColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun DifficultySelection(
    selectedDifficulty: GameDifficulty,
    onDifficultySelected: (GameDifficulty) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DifficultyChip(
            text = stringResource(R.string.difficulty_easy),
            selected = selectedDifficulty == GameDifficulty.EASY,
            onClick = { onDifficultySelected(GameDifficulty.EASY) },
            modifier = Modifier.weight(1f)
        )
        DifficultyChip(
            text = stringResource(R.string.difficulty_normal),
            selected = selectedDifficulty == GameDifficulty.NORMAL,
            onClick = { onDifficultySelected(GameDifficulty.NORMAL) },
            modifier = Modifier.weight(1f)
        )
        DifficultyChip(
            text = stringResource(R.string.difficulty_hard),
            selected = selectedDifficulty == GameDifficulty.HARD,
            onClick = { onDifficultySelected(GameDifficulty.HARD) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DifficultyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        modifier = modifier
    )
}

@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun NavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
