package com.young.aircraft.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.utils.HallOfHeroesNameUtils
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBackClick: () -> Unit
) {
    val records by viewModel.records.collectAsState()
    val scoreFormatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    var recordToDelete by remember { mutableStateOf<PlayerGameData?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary Header
            HistorySummaryHeader(records, scoreFormatter)

            if (records.isEmpty()) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.history_section_records),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(records) { record ->
                        HistoryItemCard(
                            record = record,
                            scoreFormatter = scoreFormatter,
                            onDeleteClick = { recordToDelete = record }
                        )
                    }
                }
            }
        }

        recordToDelete?.let { record ->
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text(stringResource(R.string.history_delete_title)) },
                text = { Text(stringResource(R.string.history_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteRecord(record)
                        recordToDelete = null
                    }) {
                        Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text(stringResource(R.string.history_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun HistorySummaryHeader(records: List<PlayerGameData>, scoreFormatter: NumberFormat) {
    val topRecord = records.firstOrNull()
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.history_summary_record_count, records.size)) }
                )
                SuggestionChip(
                    onClick = { },
                    label = { 
                        val bestScoreText = if (topRecord == null) {
                            stringResource(R.string.history_summary_best_score_empty)
                        } else {
                            stringResource(R.string.history_summary_best_score, scoreFormatter.format(topRecord.score))
                        }
                        Text(bestScoreText)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (topRecord == null) {
                    stringResource(R.string.history_summary_empty_description)
                } else {
                    stringResource(
                        R.string.history_summary_with_top_pilot,
                        HallOfHeroesNameUtils.getDisplayName(topRecord),
                        topRecord.level
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    record: PlayerGameData,
    scoreFormatter: NumberFormat,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Jet Icon
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Image(
                    painter = painterResource(id = record.jetPlaneRes),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = HallOfHeroesNameUtils.getDisplayName(record),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.level, record.level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = scoreFormatter.format(record.score),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_no_records),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.history_empty_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
