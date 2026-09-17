package com.young.aircraft.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.young.aircraft.R
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.CameraScanUiState
import com.young.aircraft.viewmodel.CameraScanViewModel

/**
 * Debug-only live QR scanner: CameraX preview + MlKitAnalyzer (QR only).
 * The first detected code locks the result; "rescan" clears it and resumes.
 */
class CameraScanActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this, CameraScanViewModel.Factory())[CameraScanViewModel::class.java]
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
                    onCopy = ::copyScanResult,
                    onRescan = { viewModel.resetScan() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun copyScanResult(result: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", result))
        Toast.makeText(this, R.string.qr_code_tool_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CameraScanScreen(
    uiState: CameraScanUiState,
    hasCameraPermission: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onCameraError: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCopy: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (hasCameraPermission && uiState.errorRes == null) {
            LivePreview(
                locked = uiState.result != null,
                onBarcode = onBarcodeDetected,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize()
            )
        }

        CameraScanHeader(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasCameraPermission && uiState.errorRes == null && uiState.result == null) {
                Text(
                    text = stringResource(R.string.qr_code_tool_scan_hint),
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

            uiState.result?.let { result ->
                ResultCard(
                    result = result,
                    onCopy = { onCopy(result) },
                    onRescan = onRescan
                )
            }
        }
    }
}

@Composable
private fun CameraScanHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val backDescription = stringResource(R.string.camera_scan_back)
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
            text = stringResource(R.string.camera_scan_title),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun LivePreview(
    locked: Boolean,
    onBarcode: (String) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    DisposableEffect(locked) {
        if (locked) {
            controller?.unbind()
            previewView.controller = null
        } else {
            val activity = context as? ComponentActivity
            if (activity != null) {
                val newController = LifecycleCameraController(context).apply {
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    setImageAnalysisAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        MlKitAnalyzer(
                            listOf(barcodeScanner),
                            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                            ContextCompat.getMainExecutor(context)
                        ) { result ->
                            val barcode = result?.getValue(barcodeScanner)?.firstOrNull()
                                ?: return@MlKitAnalyzer
                            barcode.rawValue?.let(onBarcode)
                        }
                    )
                }
                // CameraX resolves an available lens for DEFAULT_BACK_CAMERA itself;
                // a bind failure here means the camera is unusable, not missing.
                runCatching { newController.bindToLifecycle(lifecycleOwner) }
                    .onFailure { onCameraError() }
                controller = newController
                previewView.controller = newController
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
            barcodeScanner.close()
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
