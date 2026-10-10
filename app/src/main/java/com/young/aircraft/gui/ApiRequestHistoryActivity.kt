package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.data.ApiRequestHistory
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.viewmodel.ApiDebugToolViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApiRequestHistoryActivity : BaseAircraftActivity() {

    private lateinit var viewModel: ApiDebugToolViewModel

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(
            this,
            ApiDebugToolViewModel.Factory(this)
        )[ApiDebugToolViewModel::class.java]
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                ApiRequestHistoryScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onLoad = { id ->
                        startActivity(
                            Intent(this, ApiDebugToolActivity::class.java)
                                .putExtra(ApiDebugToolActivity.EXTRA_HISTORY_ID, id)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiRequestHistoryScreen(
    viewModel: ApiDebugToolViewModel,
    onBack: () -> Unit,
    onLoad: (Long) -> Unit
) {
    val history by viewModel.history.collectAsState()
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.api_debug_history_page_title),
                            color = AccentGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.25.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_back")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_header_back),
                                contentDescription = stringResource(R.string.history_back),
                                tint = AccentGreen
                            )
                        }
                    },
                    actions = {
                        if (history.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_history_clear")
                            ) {
                                Text(
                                    text = stringResource(R.string.api_debug_history_clear),
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = HeaderBackground,
                        scrolledContainerColor = HeaderBackground,
                        navigationIconContentColor = Color.Unspecified,
                        titleContentColor = Color.Unspecified,
                        actionIconContentColor = Color.Unspecified
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                )
                NeonDivider()
            }
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.api_debug_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("history_empty")
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .maxContentWidth()
                    .padding(innerPadding)
                    .testTag("history_list"),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryDetailItem(
                        history = item,
                        requestHeaders = viewModel.parseStoredRequestHeaders(item.requestHeaders),
                        responseHeaders = viewModel.parseStoredResponseHeaders(item.responseHeaders),
                        formattedBody = item.responseBody?.let { viewModel.formatJsonResponse(it) },
                        onLoad = { onLoad(item.id) },
                        onDelete = { viewModel.deleteHistory(item.id) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    text = stringResource(R.string.api_debug_history_clear_title),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.api_debug_history_clear_message),
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("btn_history_clear_confirm")
                ) {
                    Text(
                        text = stringResource(R.string.api_debug_clear_button),
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = stringResource(R.string.history_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryDetailItem(
    history: ApiRequestHistory,
    requestHeaders: Map<String, String>,
    responseHeaders: Map<String, List<String>>,
    formattedBody: String?,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = history.method,
                    color = colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                        .border(
                            1.dp,
                            colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = history.url,
                    color = colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = timeFormatter.format(Date(history.timestamp)),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                ExpandChevron(
                    expanded = expanded,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (history.responseCode != null) {
                Text(
                    text = stringResource(R.string.api_debug_status_format, history.responseCode, ""),
                    color = if (history.responseCode in 200..299) {
                        colorScheme.primary
                    } else {
                        colorScheme.error
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (history.error != null) {
                Text(
                    text = history.error,
                    color = colorScheme.error,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (expanded) {
                SelectionContainer {
                    Column {
                        DetailSectionLabel(R.string.api_debug_history_detail_request)
                        DetailMonoText(history.url)
                        requestHeaders.forEach { (key, value) ->
                            DetailMonoText("$key: $value")
                        }
                        if (!history.requestBody.isNullOrBlank()) {
                            DetailMonoText(history.requestBody)
                        }

                        DetailSectionLabel(R.string.api_debug_history_detail_response)
                        if (responseHeaders.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.api_debug_history_response_headers),
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            responseHeaders.forEach { (key, values) ->
                                DetailMonoText("$key: ${values.joinToString(", ")}")
                            }
                        }
                        if (!formattedBody.isNullOrBlank()) {
                            DetailMonoText(formattedBody)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLoad,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_history_load"),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.api_debug_history_load),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_history_delete"),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        colorScheme.error.copy(alpha = 0.3f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.api_debug_history_delete),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Expand/collapse chevron drawn with Canvas: the project has no material-icons
 * dependency and no chevron drawable, so a vector icon would need a new asset.
 * Decorative only — the row itself carries Role.Button semantics.
 */
@Composable
private fun ExpandChevron(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val width = size.width
        val height = size.height
        val chevron = Path().apply {
            if (expanded) {
                moveTo(width * 0.2f, height * 0.65f)
                lineTo(width * 0.5f, height * 0.35f)
                lineTo(width * 0.8f, height * 0.65f)
            } else {
                moveTo(width * 0.2f, height * 0.35f)
                lineTo(width * 0.5f, height * 0.65f)
                lineTo(width * 0.8f, height * 0.35f)
            }
        }
        drawPath(
            path = chevron,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun DetailSectionLabel(titleRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = stringResource(titleRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun DetailMonoText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
}
