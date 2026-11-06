package com.young.developtools.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.young.developtools.R
import com.young.developtools.data.BankCard
import com.young.developtools.data.label
import com.young.developtools.ui.maxContentWidth
import com.young.developtools.ui.theme.AccentGreen
import com.young.developtools.ui.theme.AircraftTheme
import com.young.developtools.ui.theme.BackgroundDark
import com.young.developtools.ui.theme.TextSubtle
import com.young.developtools.utils.DeveloperMode
import com.young.developtools.viewmodel.BankCardScanUiState
import com.young.developtools.viewmodel.BankCardScanViewModel
import com.young.developtools.viewmodel.ScanLens
import kotlin.math.max

/**
 * Debug-only live bank card scanner: CameraX preview + ML Kit Text Recognition
 * (Latin, bundled on-device model) + Luhn check, plus a gallery image path.
 * First Luhn-valid card locks the result; "rescan" clears it and resumes.
 * On-device only — the number is never stored or uploaded.
 */
class BankCardScanActivity : BaseAircraftActivity() {

    private lateinit var viewModel: BankCardScanViewModel
    private var clearClipboardJob: Job? = null
    private var hasCameraPermission by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.resetScan()
        } else {
            viewModel.setError(R.string.qr_code_tool_camera_permission_denied)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            // Cap long side at 2048 px: uncapped decode of a 48 MP photo needs ~400 MB.
            // Two-pass sample-size mirrors QRCodeToolActivity's downscale guard.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@registerForActivityResult
            try {
                viewModel.recognizeImage(InputImage.fromBitmap(bitmap, 0))
            } finally {
                bitmap.recycle()
            }
        }.onFailure {
            viewModel.setError(R.string.bank_card_scan_error)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var longSide = max(width, height)
        while (longSide > maxDimension * 2 && sampleSize < 32) {
            longSide /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        if (!DeveloperMode.isEnabled(this)) {
            finish()
            return
        }
        viewModel = ViewModelProvider(this, BankCardScanViewModel.Factory())[BankCardScanViewModel::class.java]
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        setContent {
            AircraftTheme {
                val uiState by viewModel.uiState.collectAsState()
                BankCardScanScreen(
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    onTextRecognized = viewModel::onTextRecognized,
                    onCameraError = { viewModel.setError(R.string.qr_code_tool_camera_error) },
                    onSwitchLens = viewModel::toggleLens,
                    onLensUnavailable = {
                        viewModel.useRearLens()
                        ThemedMessage.makeText(
                            this,
                            R.string.camera_scan_front_unavailable,
                            ThemedMessage.LENGTH_SHORT
                        ).show()
                    },
                    onGrantPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                        )
                    },
                    onPickImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCopy = ::copyCardNumber,
                    onRescan = { viewModel.resetScan() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun copyCardNumber(card: BankCard) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // Mark sensitive so keyboard / synced clipboard clients hide it; clear after 60 s.
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Card number", card.number).apply {
                description.extras?.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        )
        clearClipboardJob?.cancel()
        clearClipboardJob = lifecycleScope.launch {
            delay(60_000)
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        ThemedMessage.makeText(this, R.string.qr_code_tool_copied, ThemedMessage.LENGTH_SHORT).show()
    }
}

@Composable
private fun BankCardScanScreen(
    uiState: BankCardScanUiState,
    hasCameraPermission: Boolean,
    onTextRecognized: (String) -> Unit,
    onCameraError: () -> Unit,
    onSwitchLens: () -> Unit,
    onLensUnavailable: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPickImage: () -> Unit,
    onCopy: (BankCard) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (hasCameraPermission && uiState.errorRes == null) {
            LivePreview(
                locked = uiState.card != null,
                lens = uiState.lens,
                onText = onTextRecognized,
                onLensUnavailable = onLensUnavailable,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize()
            )
        }

        BankCardScanHeader(
            onBack = onBack,
            showLensSwitch = hasCameraPermission &&
                uiState.errorRes == null &&
                uiState.card == null,
            lens = uiState.lens,
            onSwitchLens = onSwitchLens,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .maxContentWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasCameraPermission && uiState.errorRes == null && uiState.card == null) {
                Text(
                    text = stringResource(R.string.bank_card_scan_hint),
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Text(
                text = stringResource(R.string.bank_card_scan_privacy_note),
                color = TextSubtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            uiState.errorRes?.let { res ->
                ErrorCard(
                    message = stringResource(res),
                    primaryText = if (hasCameraPermission) {
                        stringResource(R.string.camera_scan_retry)
                    } else {
                        stringResource(R.string.camera_scan_open_settings)
                    },
                    // Retry clears the error so the preview rebinds; it must not
                    // re-request the camera permission the user already granted.
                    onPrimary = if (hasCameraPermission) onRescan else onOpenAppSettings
                )
            }

            if (!hasCameraPermission && uiState.errorRes == null) {
                ErrorCard(
                    message = stringResource(R.string.bank_card_scan_permission_hint),
                    primaryText = stringResource(R.string.camera_scan_grant),
                    onPrimary = onGrantPermission
                )
            }

            Button(
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_pick_card_image"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen.copy(alpha = 0.18f),
                    contentColor = AccentGreen
                )
            ) {
                Text(stringResource(R.string.bank_card_scan_pick_image), fontSize = 13.sp)
            }

            uiState.card?.let { card ->
                ResultCard(
                    card = card,
                    onCopy = { onCopy(card) },
                    onRescan = onRescan
                )
            }

            if (uiState.fullText.isNotBlank()) {
                Text(
                    text = stringResource(R.string.bank_card_scan_full_text_label),
                    color = AccentGreen.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                SelectionContainer {
                    Text(
                        text = uiState.fullText,
                        color = TextSubtle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun BankCardScanHeader(
    onBack: () -> Unit,
    showLensSwitch: Boolean,
    lens: ScanLens,
    onSwitchLens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backDescription = stringResource(R.string.bank_card_scan_back)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Text(
                text = "←",
                color = AccentGreen,
                fontSize = 20.sp,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = backDescription
                }
            )
        }
        Text(
            text = stringResource(R.string.bank_card_scan_title),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(start = 2.dp)
                .weight(1.0f)
                .maxContentWidth(),
            maxLines = 1
        )
        if (showLensSwitch) {
            val switchLabel = if (lens == ScanLens.BACK) {
                stringResource(R.string.camera_scan_front)
            } else {
                stringResource(R.string.camera_scan_rear)
            }
            val switchDescription = if (lens == ScanLens.BACK) {
                stringResource(R.string.camera_scan_switch_to_front)
            } else {
                stringResource(R.string.camera_scan_switch_to_rear)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .testTag("btn_switch_camera")
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSwitchLens)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = switchDescription
                    }
            ) {
                Text(text = "⇄", color = AccentGreen, fontSize = 14.sp)
                Text(
                    text = switchLabel,
                    color = AccentGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun LivePreview(
    locked: Boolean,
    lens: ScanLens,
    onText: (String) -> Unit,
    onLensUnavailable: () -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    DisposableEffect(locked, lens) {
        if (locked) {
            controller?.unbind()
            previewView.controller = null
        } else {
            val activity = context as? ComponentActivity
            if (activity != null) {
                val selector = if (lens == ScanLens.FRONT) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val newController = LifecycleCameraController(context).apply {
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    cameraSelector = selector
                    setImageAnalysisAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        MlKitAnalyzer(
                            listOf(recognizer),
                            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                            ContextCompat.getMainExecutor(context)
                        ) { result ->
                            val visionText = result?.getValue(recognizer) ?: return@MlKitAnalyzer
                            onText(visionText.text)
                        }
                    )
                }
                runCatching { newController.bindToLifecycle(lifecycleOwner) }
                    .onSuccess {
                        controller = newController
                        previewView.controller = newController
                    }
                    .onFailure {
                        if (lens == ScanLens.FRONT) onLensUnavailable() else onCameraError()
                    }
            }
        }
        onDispose {
            controller?.unbind()
            previewView.controller = null
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })

    DisposableEffect(lifecycleOwner) {
        onDispose {
            previewView.controller = null
            recognizer.close()
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    primaryText: String,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = message,
            color = TextSubtle,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_scan_recovery"),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = BackgroundDark
            )
        ) {
            Text(primaryText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ResultCard(
    card: BankCard,
    onCopy: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.bank_card_scan_result_label),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        SelectionContainer {
            Text(
                text = card.formatted,
                color = AccentGreen,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = card.scheme.label() + "  •••• " + card.last4,
            color = TextSubtle,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_copy_card_number"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = BackgroundDark
                )
            ) {
                Text(stringResource(R.string.bank_card_scan_copy_number), fontSize = 13.sp)
            }
            Button(
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_rescan"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen.copy(alpha = 0.18f),
                    contentColor = AccentGreen
                )
            ) {
                Text(stringResource(R.string.bank_card_scan_rescan), fontSize = 13.sp)
            }
        }
    }
}
