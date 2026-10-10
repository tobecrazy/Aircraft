package com.young.aircraft.gui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.repository.ApiDebugResponse
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.utils.apidebug.BodyError
import com.young.aircraft.utils.apidebug.CurlParseException
import com.young.aircraft.utils.apidebug.CurlRequest
import com.young.aircraft.utils.apidebug.HeaderError
import com.young.aircraft.utils.apidebug.HeaderErrorKind
import com.young.aircraft.utils.apidebug.METHODS_WITH_BODY
import com.young.aircraft.utils.apidebug.UrlError
import com.young.aircraft.utils.apidebug.parseCurl
import com.young.aircraft.viewmodel.ApiDebugToolViewModel
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class ApiDebugToolActivity : BaseAircraftActivity() {

    private lateinit var viewModel: ApiDebugToolViewModel

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(
            this,
            ApiDebugToolViewModel.Factory(this)
        )[ApiDebugToolViewModel::class.java]
        // Deep link from the history page: preload the saved request.
        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        if (historyId >= 0) {
            viewModel.loadHistoryById(historyId)
        }
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                ApiDebugToolScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenHistory = { startActivity(Intent(this, ApiRequestHistoryActivity::class.java)) }
                )
            }
        }
    }

    companion object {
        /** History record id to preload; see [ApiRequestHistoryActivity]. */
        const val EXTRA_HISTORY_ID = "extra_history_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiDebugToolScreen(
    viewModel: ApiDebugToolViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCurlDialog by rememberSaveable { mutableStateOf(false) }

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
                            text = stringResource(R.string.api_debug_tool_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .maxContentWidth()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Request configuration section
            SectionHeader(R.string.api_debug_section_request)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BadgePill(
                        text = stringResource(R.string.api_debug_badge),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = stringResource(R.string.api_debug_tool_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Text(
                        text = stringResource(R.string.api_debug_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    // URL input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showCurlDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_import_curl")
                        ) {
                            Text(
                                text = stringResource(R.string.api_debug_import_curl),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = { viewModel.updateUrl(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_url"),
                        label = { Text(stringResource(R.string.api_debug_host_label)) },
                        placeholder = { Text(stringResource(R.string.api_debug_host_hint)) },
                        minLines = 1,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        shape = MaterialTheme.shapes.small,
                        isError = uiState.urlError != null,
                        supportingText = urlErrorText(uiState.urlError)?.let { { Text(it) } },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Method dropdown
                    MethodDropdown(
                        selected = uiState.method,
                        onSelect = { viewModel.updateMethod(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )

                    // Headers input
                    OutlinedTextField(
                        value = uiState.headers,
                        onValueChange = { viewModel.updateHeaders(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .heightIn(min = 80.dp)
                            .testTag("input_headers"),
                        label = { Text(stringResource(R.string.api_debug_headers_label)) },
                        placeholder = { Text(stringResource(R.string.api_debug_headers_hint)) },
                        maxLines = 5,
                        shape = MaterialTheme.shapes.small,
                        isError = uiState.headerErrors.isNotEmpty(),
                        supportingText = headerErrorText(uiState.headerErrors)?.let { { Text(it) } },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Request body: shown for body methods, or whenever a body was
                    // filled in (e.g. imported) so a NOT_ALLOWED error stays visible.
                    if (uiState.method in METHODS_WITH_BODY || uiState.requestBody.isNotBlank()) {
                        OutlinedTextField(
                            value = uiState.requestBody,
                            onValueChange = { viewModel.updateRequestBody(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .heightIn(min = 120.dp)
                                .testTag("input_body"),
                            label = { Text(stringResource(R.string.api_debug_request_body_label)) },
                            placeholder = { Text(stringResource(R.string.api_debug_request_body_hint)) },
                            maxLines = 8,
                            shape = MaterialTheme.shapes.small,
                            isError = uiState.bodyError != null,
                            supportingText = bodyErrorText(uiState.bodyError)?.let { { Text(it) } },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendRequest() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_send"),
                            shape = MaterialTheme.shapes.extraSmall,
                            enabled = uiState.canSend
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .width(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.api_debug_send_button),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearAll() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_clear"),
                            shape = MaterialTheme.shapes.extraSmall,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.api_debug_clear_button),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Response section
            if (uiState.response != null || uiState.error != null) {
                SectionHeader(R.string.api_debug_section_response)

                ResponsePanel(
                    response = uiState.response,
                    error = uiState.error,
                    formatJson = { viewModel.formatJsonResponse(it) }
                )
            }

            // History entrance: records live on the dedicated history page.
            SectionHeader(R.string.api_debug_section_history)

            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 48.dp)
                    .testTag("btn_view_all_history"),
                shape = MaterialTheme.shapes.extraSmall,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.api_debug_history_view_all),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showCurlDialog) {
        CurlImportDialog(
            onDismiss = { showCurlDialog = false },
            onImported = { curl ->
                viewModel.importCurl(curl)
                ThemedMessage.makeText(
                    context,
                    R.string.api_debug_import_curl_success,
                    ThemedMessage.LENGTH_SHORT
                ).show()
                showCurlDialog = false
            }
        )
    }
}

@Composable
private fun BadgePill(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.shapes.medium
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun urlErrorText(error: UrlError?): String? = when (error) {
    UrlError.EMPTY -> stringResource(R.string.api_debug_error_empty_url)
    UrlError.INVALID -> stringResource(R.string.api_debug_error_invalid_url)
    null -> null
}

@Composable
private fun headerErrorText(errors: List<HeaderError>): String? {
    val first = errors.firstOrNull() ?: return null
    return when (first.kind) {
        HeaderErrorKind.NO_COLON ->
            stringResource(R.string.api_debug_header_error_no_colon, first.lineNumber)
        HeaderErrorKind.EMPTY_KEY ->
            stringResource(R.string.api_debug_header_error_empty_key, first.lineNumber)
        HeaderErrorKind.INVALID_KEY ->
            stringResource(R.string.api_debug_header_error_invalid_key, first.lineNumber)
    }
}

@Composable
private fun bodyErrorText(error: BodyError?): String? = when (error) {
    BodyError.INVALID_JSON -> stringResource(R.string.api_debug_error_invalid_body_json)
    BodyError.NOT_ALLOWED -> stringResource(R.string.api_debug_error_body_not_allowed)
    null -> null
}

@Composable
private fun SectionHeader(titleRes: Int) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = stringResource(titleRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE", "CONNECT")
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.testTag("dropdown_method")
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.api_debug_method_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.small,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.primary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            methods.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = method,
                            color = if (method == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    onClick = {
                        onSelect(method)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun CurlImportDialog(
    onDismiss: () -> Unit,
    onImported: (CurlRequest) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var command by rememberSaveable { mutableStateOf("") }
    var parseFailed by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerLow,
        titleContentColor = colorScheme.primary,
        textContentColor = colorScheme.onSurface,
        title = {
            Text(
                text = stringResource(R.string.api_debug_import_curl),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            OutlinedTextField(
                value = command,
                onValueChange = {
                    command = it
                    parseFailed = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .testTag("input_curl"),
                placeholder = { Text(stringResource(R.string.api_debug_import_curl_hint)) },
                maxLines = 12,
                shape = MaterialTheme.shapes.small,
                isError = parseFailed,
                supportingText = if (parseFailed) {
                    { Text(stringResource(R.string.api_debug_import_curl_error)) }
                } else {
                    null
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outlineVariant,
                    cursorColor = colorScheme.primary,
                    focusedLabelColor = colorScheme.primary,
                    unfocusedLabelColor = colorScheme.onSurfaceVariant
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        onImported(parseCurl(command))
                    } catch (e: CurlParseException) {
                        parseFailed = true
                    }
                },
                enabled = command.isNotBlank(),
                modifier = Modifier.testTag("btn_curl_import")
            ) {
                Text(
                    text = stringResource(R.string.api_debug_import_curl),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.history_cancel))
            }
        }
    )
}

@Composable
private fun ResponsePanel(
    response: ApiDebugResponse?,
    error: String?,
    formatJson: (String) -> String
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (error != null) {
                colorScheme.error.copy(alpha = 0.5f)
            } else if (response != null && response.statusCode in 200..299) {
                colorScheme.primary.copy(alpha = 0.5f)
            } else {
                colorScheme.outline
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (error != null) {
                Text(
                    text = stringResource(R.string.api_debug_error_network, error),
                    color = colorScheme.error,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else if (response != null) {
                // Response body
                val formattedBody = remember(response.body) {
                    formatJson(response.body)
                }
                val context = LocalContext.current
                // Resolved in composition so the copied text stays configuration-aware (lint).
                val statusLine = stringResource(
                    R.string.api_debug_status_format,
                    response.statusCode,
                    response.statusMessage
                )
                val timeLine = stringResource(
                    R.string.api_debug_time_format,
                    response.responseTime
                )
                val sizeLine = stringResource(
                    R.string.api_debug_size_format,
                    formatFileSize(response.responseSize)
                )

                // Status info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            R.string.api_debug_status_format,
                            response.statusCode,
                            response.statusMessage
                        ),
                        color = if (response.statusCode in 200..299) {
                            colorScheme.primary
                        } else {
                            colorScheme.error
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.api_debug_time_format, response.responseTime),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.api_debug_size_format,
                            formatFileSize(response.responseSize)
                        ),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(
                        onClick = {
                            val copyText = statusLine + "\n" + timeLine + "\n" + sizeLine + "\n\n" + formattedBody
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("API Response", copyText))
                            ThemedMessage.makeText(
                                context,
                                R.string.api_debug_copied,
                                ThemedMessage.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_copy_response")
                    ) {
                        Text(
                            text = stringResource(R.string.api_debug_copy_button),
                            color = colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Response body: selectable + internally scrollable, capped at 400dp
                Surface(
                    color = colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = formattedBody,
                            color = colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                                .testTag("response_body")
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.api_debug_no_response),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024
    val exp = (log10(bytes.toDouble()) / log10(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}
