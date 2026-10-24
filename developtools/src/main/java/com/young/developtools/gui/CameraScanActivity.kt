package com.young.developtools.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.young.developtools.R
import com.young.developtools.ui.maxContentWidth
import com.young.developtools.ui.theme.AccentGreen
import com.young.developtools.ui.theme.AircraftTheme
import com.young.developtools.ui.theme.BackgroundDark
import com.young.developtools.ui.theme.TextSubtle
import com.young.developtools.utils.DeveloperMode
import com.young.developtools.viewmodel.CameraLens
import com.young.developtools.viewmodel.CameraScanMode
import com.young.developtools.viewmodel.CameraScanUiState
import com.young.developtools.viewmodel.CameraScanViewModel
import com.young.developtools.viewmodel.CardModelStatus
import com.young.developtools.viewmodel.CardNumberValidator

/**
 * Debug-only live scanner: CameraX preview plus MlKitAnalyzer.
 *
 * QR mode decodes barcodes. Card mode runs on-device text recognition and
 * sends OCR text (never the image) to the ML Kit GenAI Prompt API, then
 * validates the candidate locally. The first accepted result locks the
 * preview; "rescan" clears it and resumes.
 */
class CameraScanActivity : BaseAircraftActivity() {

    private lateinit var viewModel: CameraScanViewModel
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

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        if (!DeveloperMode.isEnabled(this)) {
            finish()
            return
        }
        viewModel = ViewModelProvider(
            this,
            viewModelFactoryOverride ?: CameraScanViewModel.Factory()
        )[CameraScanViewModel::class.java]
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        setContent {
            AircraftTheme {
                val uiState by viewModel.uiState.collectAsState()
                CameraScanScreen(
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    onBarcodeDetected = viewModel::onBarcodeDetected,
                    onCardText = viewModel::onCardFrameText,
                    onCameraError = { viewModel.setError(R.string.qr_code_tool_camera_error) },
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
                    onCopyQr = ::copyQrResult,
                    onCopyCardNumber = ::copyCardNumber,
                    onRescanQr = { viewModel.resetScan() },
                    onRescanCard = { viewModel.rescanCard() },
                    onSelectMode = { viewModel.setMode(it) },
                    onSwitchCamera = { viewModel.toggleLens() },
                    onCheckCardModel = { viewModel.checkCardModelAvailability() },
                    onDownloadCardModel = { viewModel.downloadCardModel() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun copyQrResult(result: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", result))
        ThemedMessage.makeText(this, R.string.qr_code_tool_copied, ThemedMessage.LENGTH_SHORT).show()
    }

    /** Copies the full PAN only after an explicit tap and marks it sensitive. */
    private fun copyCardNumber(cardNumber: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Card Number", cardNumber)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        ThemedMessage.makeText(
            this,
            R.string.camera_scan_card_copied,
            ThemedMessage.LENGTH_SHORT
        ).show()
    }

    companion object {
        /** Test seam so UI tests can run without AICore or a downloaded model. */
        internal var viewModelFactoryOverride: ViewModelProvider.Factory? = null
    }
}

@Composable
private fun CameraScanScreen(
    uiState: CameraScanUiState,
    hasCameraPermission: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onCardText: (String) -> Unit,
    onCameraError: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCopyQr: (String) -> Unit,
    onCopyCardNumber: (String) -> Unit,
    onRescanQr: () -> Unit,
    onRescanCard: () -> Unit,
    onSelectMode: (CameraScanMode) -> Unit,
    onSwitchCamera: () -> Unit,
    onCheckCardModel: () -> Unit,
    onDownloadCardModel: () -> Unit,
    onBack: () -> Unit
) {
    val cardLocked = (uiState.cardModelStatus != CardModelStatus.AVAILABLE &&
        uiState.cardModelStatus != CardModelStatus.LOCAL_ONLY) ||
        uiState.cardNumber != null || uiState.cardErrorRes != null
    val locked = when (uiState.mode) {
        CameraScanMode.QR -> uiState.result != null
        CameraScanMode.CARD -> cardLocked
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (hasCameraPermission && uiState.errorRes == null) {
            LivePreview(
                mode = uiState.mode,
                lensFacing = uiState.lensFacing,
                locked = locked,
                onBarcode = onBarcodeDetected,
                onCardText = onCardText,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize()
            )
        }

        CameraScanHeader(
            mode = uiState.mode,
            lensFacing = uiState.lensFacing,
            onSwitchCamera = onSwitchCamera,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        ModeToggle(
            mode = uiState.mode,
            onSelect = onSelectMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .maxContentWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasCameraPermission && uiState.errorRes == null && !locked) {
                Text(
                    text = stringResource(
                        when (uiState.mode) {
                            CameraScanMode.QR -> R.string.qr_code_tool_scan_hint
                            CameraScanMode.CARD -> R.string.camera_scan_card_hint
                        }
                    ),
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            uiState.errorRes?.let { res ->
                ErrorCard(
                    message = stringResource(res),
                    primaryText = if (hasCameraPermission) {
                        stringResource(R.string.camera_scan_retry)
                    } else {
                        stringResource(R.string.camera_scan_open_settings)
                    },
                    onPrimary = if (hasCameraPermission) onGrantPermission else onOpenAppSettings
                )
            }

            if (!hasCameraPermission && uiState.errorRes == null) {
                ErrorCard(
                    message = stringResource(R.string.camera_scan_permission_hint),
                    primaryText = stringResource(R.string.camera_scan_grant),
                    onPrimary = onGrantPermission
                )
            }

            when (uiState.mode) {
                CameraScanMode.QR -> {
                    uiState.result?.let { result ->
                        ResultCard(
                            result = result,
                            onCopy = { onCopyQr(result) },
                            onRescan = onRescanQr
                        )
                    }
                }
                CameraScanMode.CARD -> CardScanContent(
                    uiState = uiState,
                    onCheckCardModel = onCheckCardModel,
                    onDownloadCardModel = onDownloadCardModel,
                    onCopyCardNumber = onCopyCardNumber,
                    onRescanCard = onRescanCard
                )
            }
        }
    }
}

@Composable
private fun CardScanContent(
    uiState: CameraScanUiState,
    onCheckCardModel: () -> Unit,
    onDownloadCardModel: () -> Unit,
    onCopyCardNumber: (String) -> Unit,
    onRescanCard: () -> Unit
) {
    when (uiState.cardModelStatus) {
        CardModelStatus.CHECKING -> StatusCard(
            message = stringResource(R.string.camera_scan_card_model_checking),
            tag = "tv_card_model_state"
        )
        CardModelStatus.UNAVAILABLE -> ErrorCard(
            message = stringResource(R.string.camera_scan_card_model_unavailable),
            primaryText = stringResource(R.string.camera_scan_retry),
            onPrimary = onCheckCardModel,
            tag = "btn_card_model_recovery"
        )
        CardModelStatus.DOWNLOAD_REQUIRED -> {
            uiState.cardModelErrorRes?.let { res ->
                StatusCard(message = stringResource(res), tag = "tv_card_model_error")
            }
            ErrorCard(
                message = stringResource(R.string.camera_scan_card_model_download_required),
                primaryText = stringResource(R.string.camera_scan_card_download),
                onPrimary = onDownloadCardModel,
                tag = "btn_card_model_recovery"
            )
        }
        CardModelStatus.DOWNLOADING -> StatusCard(
            message = stringResource(
                R.string.camera_scan_card_downloading,
                uiState.cardDownloadProgress ?: 0
            ),
            tag = "tv_card_model_state"
        )
        CardModelStatus.AVAILABLE, CardModelStatus.LOCAL_ONLY -> {
            val cardNumber = uiState.cardNumber
            val errorRes = uiState.cardErrorRes
            when {
                cardNumber != null -> CardResultCard(
                    maskedNumber = CardNumberValidator.mask(cardNumber),
                    onCopy = { onCopyCardNumber(cardNumber) },
                    onRescan = onRescanCard
                )
                errorRes != null -> ErrorCard(
                    message = stringResource(errorRes),
                    primaryText = stringResource(R.string.camera_scan_rescan),
                    onPrimary = onRescanCard,
                    tag = "btn_card_error_rescan"
                )
                else -> StatusCard(
                    message = stringResource(
                        when {
                            uiState.isCardAnalyzing -> R.string.camera_scan_card_scanning
                            uiState.cardModelStatus == CardModelStatus.LOCAL_ONLY ->
                                R.string.camera_scan_card_local_only
                            else -> R.string.camera_scan_card_hint
                        }
                    ),
                    tag = "tv_card_scan_hint"
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.camera_scan_card_privacy),
        color = TextSubtle.copy(alpha = 0.8f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("tv_card_privacy")
    )
}

@Composable
private fun CameraScanHeader(
    mode: CameraScanMode,
    lensFacing: CameraLens,
    onSwitchCamera: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backDescription = stringResource(R.string.camera_scan_back)
    val switchDescription = stringResource(
        if (lensFacing == CameraLens.BACK) {
            R.string.camera_scan_switch_to_front
        } else {
            R.string.camera_scan_switch_to_rear
        }
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
            text = stringResource(
                when (mode) {
                    CameraScanMode.QR -> R.string.camera_scan_title
                    CameraScanMode.CARD -> R.string.camera_scan_card_title
                }
            ),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier.testTag("btn_switch_camera")
        ) {
            Text(
                text = "⇄",
                color = AccentGreen,
                fontSize = 20.sp,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = switchDescription
                }
            )
        }
    }
}

@Composable
private fun ModeToggle(
    mode: CameraScanMode,
    onSelect: (CameraScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(BackgroundDark.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.27f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeButton(
            label = stringResource(R.string.camera_scan_mode_qr),
            selected = mode == CameraScanMode.QR,
            tag = "btn_scan_mode_qr",
            onClick = { onSelect(CameraScanMode.QR) }
        )
        ModeButton(
            label = stringResource(R.string.camera_scan_mode_card),
            selected = mode == CameraScanMode.CARD,
            tag = "btn_scan_mode_card",
            onClick = { onSelect(CameraScanMode.CARD) }
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(38.dp)
            .testTag(tag),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentGreen else AccentGreen.copy(alpha = 0.16f),
            contentColor = if (selected) BackgroundDark else AccentGreen
        )
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LivePreview(
    mode: CameraScanMode,
    lensFacing: CameraLens,
    locked: Boolean,
    onBarcode: (String) -> Unit,
    onCardText: (String) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    DisposableEffect(mode, lensFacing, locked) {
        var closeDetector: (() -> Unit)? = null
        if (locked) {
            controller?.unbind()
            previewView.controller = null
        } else {
            val activity = context as? ComponentActivity
            if (activity != null) {
                val newController = LifecycleCameraController(context).apply {
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    cameraSelector = resolveCameraSelector(context, lensFacing)
                }
                val mainExecutor = ContextCompat.getMainExecutor(context)
                when (mode) {
                    CameraScanMode.QR -> {
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )
                        closeDetector = scanner::close
                        newController.setImageAnalysisAnalyzer(
                            mainExecutor,
                            MlKitAnalyzer(
                                listOf(scanner),
                                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                                mainExecutor
                            ) { result ->
                                result?.getValue(scanner)?.firstOrNull()?.rawValue?.let(onBarcode)
                            }
                        )
                    }
                    CameraScanMode.CARD -> {
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        closeDetector = recognizer::close
                        newController.setImageAnalysisAnalyzer(
                            mainExecutor,
                            MlKitAnalyzer(
                                listOf(recognizer),
                                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                                mainExecutor
                            ) { result ->
                                result?.getValue(recognizer)?.text
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(onCardText)
                            }
                        )
                    }
                }
                // A bind failure here means the camera is unusable, not missing.
                runCatching { newController.bindToLifecycle(lifecycleOwner) }
                    .onFailure { onCameraError() }
                controller = newController
                previewView.controller = newController
            }
        }
        onDispose {
            controller?.unbind()
            previewView.controller = null
            closeDetector?.invoke()
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })
}

/**
 * Resolves the requested lens, falling back to the rear camera when the device has no
 * usable front camera. Callers still treat a bind failure as a camera error.
 */
private fun resolveCameraSelector(context: Context, lensFacing: CameraLens): CameraSelector {
    return if (lensFacing == CameraLens.FRONT && hasFrontCamera(context)) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }
}

/** Checks the camera service directly because CameraX's provider is not ready yet. */
private fun hasFrontCamera(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(CameraManager::class.java)
    manager.cameraIdList.any { id ->
        manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    }
}.getOrDefault(false)

@Composable
private fun StatusCard(message: String, tag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag(tag)
    ) {
        Text(
            text = message,
            color = TextSubtle,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    primaryText: String,
    onPrimary: () -> Unit,
    tag: String = "btn_scan_recovery"
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
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
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
    result: String,
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
            text = stringResource(R.string.qr_code_tool_scan_result_label),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        SelectionContainer {
            Text(
                text = result,
                color = AccentGreen,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_copy_scan_result"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = BackgroundDark
                )
            ) {
                Text(stringResource(R.string.qr_code_tool_copy_result), fontSize = 13.sp)
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
                Text(stringResource(R.string.camera_scan_rescan), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CardResultCard(
    maskedNumber: String,
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
            text = stringResource(R.string.camera_scan_card_result_label),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        SelectionContainer {
            Text(
                text = maskedNumber,
                color = AccentGreen,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("tv_card_masked_number")
            )
        }
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
                Text(stringResource(R.string.camera_scan_card_copy), fontSize = 13.sp)
            }
            Button(
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_card_rescan"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen.copy(alpha = 0.18f),
                    contentColor = AccentGreen
                )
            ) {
                Text(stringResource(R.string.camera_scan_rescan), fontSize = 13.sp)
            }
        }
    }
}
