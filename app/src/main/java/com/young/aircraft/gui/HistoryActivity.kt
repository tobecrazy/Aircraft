package com.young.aircraft.gui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.utils.HallOfHeroesNameUtils
import com.young.aircraft.viewmodel.HistoryUiState
import com.young.aircraft.viewmodel.HistoryViewModel
import java.text.NumberFormat
import java.util.Locale

// Palette lifted from HistoryAdapter / leaderboard drawables.
private val TopGold = Color(0xFFFFD45A)
private val ScoreYellow = Color(0xFFFFFF00)
private val RankOnAccent = Color(0xFF1B1F2B)
private val ColumnHeader = Color(0xFF7F8AA3)
private val SectionLabel = Color(0x66FFFFFF)
private val DeleteIconTint = Color(0xFFFF7B7B)

// 7-color rainbow cycle: red, orange, yellow, green, cyan, blue, purple
private val RAINBOW_COLORS = listOf(
    Color(0xFFFF4444),
    Color(0xFFFF8C00),
    Color(0xFFFFD700),
    Color(0xFF00CC66),
    Color(0xFF00CED1),
    Color(0xFF4488FF),
    Color(0xFFAA66CC)
)

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: HistoryViewModel
    private val scoreFormatter = NumberFormat.getNumberInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dao = DatabaseProvider.getDatabase(applicationContext).playerGameDataDao()
        viewModel = ViewModelProvider(this, HistoryViewModel.Factory(dao))[HistoryViewModel::class.java]

        setContent {
            AircraftTheme {
                val state by viewModel.uiState.collectAsState()
                // Multi-column grid on wide windows (sw600dp/sw1240dp), single column on phones.
                HistoryScreen(
                    state = state,
                    spanCount = resources.getInteger(R.integer.history_span_count),
                    scoreFormatter = scoreFormatter,
                    onBack = { finish() },
                    onDelete = ::confirmDelete
                )
            }
        }
    }

    private fun confirmDelete(item: PlayerGameData) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message))
            .setPositiveButton(getString(R.string.history_delete)) { _, _ ->
                viewModel.deleteRecord(item)
            }
            .setNegativeButton(getString(R.string.history_cancel), null)
            .show()
    }
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    spanCount: Int,
    scoreFormatter: NumberFormat,
    onBack: () -> Unit,
    onDelete: (PlayerGameData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        HistoryHeader(onBack = onBack)
        NeonDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            SummaryCard(state = state)

            RecordsSectionHeader()

            ColumnHeadersRow()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.records.isEmpty() && !state.isLoading) {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                } else {
                    // ponytail: plain scrollable column, not LazyVerticalGrid — Robolectric never
                    // composes lazy-grid items and leaderboards are small; switch to lazy when
                    // record counts grow past a screenful in profiling.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val columns = spanCount.coerceAtLeast(1)
                        state.records.chunked(columns).forEachIndexed { rowIdx, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                row.forEachIndexed { colIdx, item ->
                                    RecordCard(
                                        item = item,
                                        index = rowIdx * columns + colIdx,
                                        scoreFormatter = scoreFormatter,
                                        onDelete = { onDelete(item) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
                .padding(start = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_back),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.leaderboard_title),
            modifier = Modifier.align(Alignment.Center),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@Composable
private fun SummaryCard(state: HistoryUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x3300FF88)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = stringResource(R.string.history_summary_badge),
                color = TextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color(0x18FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.history_summary_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 14.dp)
            )

            Text(
                text = if (state.topPilotName == null) {
                    stringResource(R.string.history_summary_empty_description)
                } else {
                    stringResource(
                        R.string.history_summary_with_top_pilot,
                        state.topPilotName!!,
                        state.topPilotLevel ?: 1
                    )
                },
                color = TextBody,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.history_summary_record_count, state.recordCount),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.chipModifier(active = true)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.bestScore == null) {
                        stringResource(R.string.history_summary_best_score_empty)
                    } else {
                        stringResource(
                            R.string.history_summary_best_score,
                            NumberFormat.getNumberInstance(Locale.US).format(state.bestScore)
                        )
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.chipModifier(active = false)
                )
            }
        }
    }
}

/** settings_chip_active_bg (#2600FF88/#6600FF88 pill) vs settings_chip_bg (#18FFFFFF/#28FFFFFF pill). */
private fun Modifier.chipModifier(active: Boolean): Modifier = this
    .background(
        color = if (active) Color(0x2600FF88) else Color(0x18FFFFFF),
        shape = RoundedCornerShape(percent = 50)
    )
    .border(
        width = 1.dp,
        color = if (active) Color(0x6600FF88) else Color(0x28FFFFFF),
        shape = RoundedCornerShape(percent = 50)
    )
    .padding(horizontal = 12.dp, vertical = 6.dp)

@Composable
private fun RecordsSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(AccentGreen)
        )
        Text(
            text = stringResource(R.string.history_section_records),
            color = SectionLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ColumnHeadersRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_col_player),
            color = ColumnHeader,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.history_col_score),
            color = ColumnHeader,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0x20252A3A),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_top_record_flag_star),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(AccentGreen)
            )
            Text(
                text = stringResource(R.string.history_no_records),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = stringResource(R.string.history_empty_summary),
                color = TextSubtle,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RecordCard(
    item: PlayerGameData,
    index: Int,
    scoreFormatter: NumberFormat,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTopRecord = index == 0
    val accent = if (isTopRecord) TopGold else RAINBOW_COLORS[index % RAINBOW_COLORS.size]

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isTopRecord) Color(0x26FFD45A) else accent.copy(alpha = 0x1A / 255f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTopRecord) Color(0x88FFD45A) else accent.copy(alpha = 0x55 / 255f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = accent, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = RankOnAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = HallOfHeroesNameUtils.getDisplayName(item),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isTopRecord) {
                        Row(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(
                                    color = Color(0x2900FF88),
                                    shape = RoundedCornerShape(percent = 50)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0x6600FF88),
                                    shape = RoundedCornerShape(percent = 50)
                                )
                                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_top_record_flag_star),
                                contentDescription = stringResource(R.string.history_top_record_badge),
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(TopGold)
                            )
                            Text(
                                text = stringResource(R.string.history_top_record_label),
                                color = TopGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = scoreFormatter.format(item.score),
                    color = if (isTopRecord) TopGold else ScoreYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.level, item.level.toString()),
                    color = TextBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.chipModifier(active = false)
                )

                val (difficultyText, difficultyColor) = when (
                    GameDifficulty.fromPersistedValue(item.difficulty)
                ) {
                    GameDifficulty.EASY -> stringResource(R.string.difficulty_easy) to Color(0xFF00FF88)
                    GameDifficulty.HARD -> stringResource(R.string.difficulty_hard) to Color(0xFFFF4444)
                    GameDifficulty.NORMAL -> stringResource(R.string.difficulty_normal) to Color(0xFFFFFF00)
                }
                Text(
                    text = difficultyText,
                    color = difficultyColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(difficultyColor.copy(alpha = 0x33 / 255f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_delete")
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_delete),
                        contentDescription = stringResource(R.string.history_delete),
                        tint = DeleteIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1118, widthDp = 412, heightDp = 892)
@Composable
private fun HistoryScreenPreview() {
    AircraftTheme {
        HistoryScreen(
            state = HistoryUiState(),
            spanCount = 1,
            scoreFormatter = NumberFormat.getNumberInstance(Locale.US),
            onBack = {},
            onDelete = {}
        )
    }
}
