package com.young.aircraft.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.young.aircraft.R
import com.young.aircraft.gui.dialogs.setDialogComposeContent
import com.young.aircraft.gui.dialogs.showThemed
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.TextMuted
import com.young.aircraft.ui.theme.TextSubtle
import com.young.aircraft.utils.FilePickerHelper
import com.young.aircraft.viewmodel.QRCodeToolUiState
import com.young.aircraft.viewmodel.QRCodeToolViewModel
import com.young.richtext.RichTextEditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.roundToInt

class QRCodeToolActivity : AppCompatActivity() {

    private lateinit var viewModel: QRCodeToolViewModel

    // Hosted inside AndroidView; the activity keeps references for camera plumbing and tests.
    internal var scanSurfaceView: SurfaceView? = null
        private set
    internal var richEditorView: RichTextEditorView? = null
        private set

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isCameraOpening = false
    private var frameCounter = 0

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val bitmap = viewModel.uiState.value.generatedBitmap ?: return@registerForActivityResult
        val saved = contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false
        if (saved) {
            ThemedMessage.makeText(this, R.string.qr_code_tool_save_success, ThemedMessage.LENGTH_SHORT).show()
            viewModel.onSaveSuccess(uri)
        } else {
            ThemedMessage.makeText(this, R.string.qr_code_tool_save_failed, ThemedMessage.LENGTH_SHORT).show()
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        handlePickedFileUri(uri)
    }

    private val scanSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (viewModel.uiState.value.isScanning) openCamera()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releaseCamera()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanning() else ThemedMessage.makeText(
            this, R.string.qr_code_tool_camera_permission_denied, ThemedMessage.LENGTH_SHORT
        ).show()
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        handlePickedFileUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this, QRCodeToolViewModel.Factory())[QRCodeToolViewModel::class.java]
        enableEdgeToEdge()

        setContent {
            AircraftTheme {
                val state by viewModel.uiState.collectAsState()
                QrCodeToolScreen(
                    state = state,
                    onBack = { finish() },
                    onScanToggle = ::onScanButtonClicked,
                    onGenerate = ::generateFromEditor,
                    onPickFromGallery = { pickFileLauncher.launch("image/*") },
                    onPickFromGalleryIdle = {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSave = ::saveQrToGallery,
                    onShare = ::shareQrCode,
                    onEditorCreated = { view -> richEditorView = view },
                    onEditorMessage = { message ->
                        ThemedMessage.makeText(this, message, ThemedMessage.LENGTH_SHORT).show()
                    },
                    onScanSurfaceCreated = ::attachScanSurface
                )
            }
        }
    }

    internal fun attachScanSurface(view: SurfaceView) {
        scanSurfaceView = view
        view.holder.addCallback(scanSurfaceCallback)
    }

    // ── Scan QR Code ───────────────────────────────────────

    private fun onScanButtonClicked() {
        if (viewModel.uiState.value.isScanning) {
            stopScanning()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startScanning()
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            )
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.qr_code_tool_camera_rationale_title)
                .setMessage(R.string.qr_code_tool_camera_rationale_message)
                .setPositiveButton(R.string.qr_code_tool_camera_rationale_ok) { _, _ ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton(R.string.history_cancel, null)
                .showThemed()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        viewModel.startScanning()
        frameCounter = 0
        startBackgroundThread()

        // A still-composing scan pane reports surfaceCreated and opens the camera from there;
        // a surface kept from an interrupted exit animation must be used directly.
        val surface = scanSurfaceView?.holder?.surface
        if (surface != null && surface.isValid) openCamera()
    }

    private fun stopScanning() {
        viewModel.stopScanning()
        releaseCamera()
        stopBackgroundThread()
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        if (isCameraOpening || cameraDevice != null) return
        isCameraOpening = true
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = findBackCamera(cameraManager)
        if (cameraId == null) {
            isCameraOpening = false
            runOnUiThread {
                ThemedMessage.makeText(
                    this,
                    R.string.qr_code_tool_camera_error,
                    ThemedMessage.LENGTH_SHORT
                ).show()
                stopScanning()
            }
            return
        }

        imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    frameCounter++
                    if (frameCounter % 4 != 0) return@setOnImageAvailableListener

                    val yPlane = image.planes[0]
                    val yBuffer = yPlane.buffer
                    val yBytes = ByteArray(yBuffer.remaining())
                    yBuffer.get(yBytes)

                    val source = PlanarYUVLuminanceSource(
                        yBytes, image.width, image.height,
                        0, 0, image.width, image.height, false
                    )
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    val hints = mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                    val result = MultiFormatReader().decode(binaryBitmap, hints)
                    runOnUiThread { onScanResult(result.text) }
                } catch (_: NotFoundException) {
                    // No QR code in this frame
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isCameraOpening = false
                    cameraDevice = camera
                    createPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isCameraOpening = false
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isCameraOpening = false
                    camera.close()
                    cameraDevice = null
                    runOnUiThread {
                        ThemedMessage.makeText(
                            this@QRCodeToolActivity,
                            R.string.qr_code_tool_camera_error,
                            ThemedMessage.LENGTH_SHORT
                        ).show()
                        stopScanning()
                    }
                }
            }, backgroundHandler)
        } catch (_: CameraAccessException) {
            isCameraOpening = false
            runOnUiThread {
                ThemedMessage.makeText(
                    this,
                    R.string.qr_code_tool_camera_error,
                    ThemedMessage.LENGTH_SHORT
                ).show()
                stopScanning()
            }
        }
    }

    private fun findBackCamera(cameraManager: CameraManager): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return cameraId
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val previewSurface = scanSurfaceView?.holder?.surface
        if (previewSurface == null || !previewSurface.isValid) return

        try {
            val outputConfigs = listOf(
                OutputConfiguration(previewSurface),
                OutputConfiguration(reader.surface)
            )
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        val requestBuilder =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                addTarget(reader.surface)
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                            }
                        session.setRepeatingRequest(
                            requestBuilder.build(), null, backgroundHandler
                        )
                    } catch (_: CameraAccessException) {
                        runOnUiThread { stopScanning() }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        ThemedMessage.makeText(
                            this@QRCodeToolActivity,
                            R.string.qr_code_tool_camera_error,
                            ThemedMessage.LENGTH_SHORT
                        ).show()
                        stopScanning()
                    }
                }
            }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                mainExecutor,
                stateCallback
            )
            camera.createCaptureSession(sessionConfig)
        } catch (_: CameraAccessException) {
            runOnUiThread { stopScanning() }
        }
    }

    private fun onScanResult(result: String) {
        stopScanning()
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_Aircraft_QrToolBottomSheet)
        dialog.setOnShowListener {
            dialog.findViewById<android.widget.FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        dialog.setDialogComposeContent(this) {
            ScanResultSheetContent(
                result = result,
                onCopy = ::copyScanResult,
                onDismiss = { dialog.dismiss() }
            )
        }
    }

    private fun copyScanResult(result: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", result))
        ThemedMessage.makeText(this, R.string.qr_code_tool_copied, ThemedMessage.LENGTH_SHORT).show()
    }

    private fun releaseCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        isCameraOpening = false
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("QRScanThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    // ── Save QR Code ────────────────────────────────────────

    private fun saveQrToGallery() {
        if (viewModel.uiState.value.generatedBitmap == null) return
        createDocumentLauncher.launch("QRCode_${System.currentTimeMillis()}.png")
    }

    private fun shareQrCode() {
        val bitmap = viewModel.uiState.value.generatedBitmap ?: return
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = FilePickerHelper.createQrImageFile(this@QRCodeToolActivity)
                    FileOutputStream(target).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    target
                }
                val uri = FilePickerHelper.getUriForFile(this@QRCodeToolActivity, file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.qr_code_tool_share_button)))
            } catch (_: Exception) {
                ThemedMessage.makeText(
                    this@QRCodeToolActivity,
                    R.string.qr_code_tool_save_failed,
                    ThemedMessage.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Pick QR from Gallery ──────────────────────────────────

    private fun handlePickedFileUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val cachedFile = FilePickerHelper.copyUriToCache(this@QRCodeToolActivity, uri)
                    val source = cachedFile?.inputStream()
                        ?: contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val rawBitmap = source?.use { BitmapFactory.decodeStream(it, null, options) }
                    if (rawBitmap != null && rawBitmap.config != Bitmap.Config.ARGB_8888) {
                        rawBitmap.copy(Bitmap.Config.ARGB_8888, false).also { rawBitmap.recycle() }
                    } else rawBitmap
                }
                if (bitmap == null) {
                    ThemedMessage.makeText(
                        this@QRCodeToolActivity,
                        R.string.qr_code_tool_pick_failed,
                        ThemedMessage.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val result = viewModel.decodeQrFromBitmap(bitmap)
                if (result != null) {
                    onScanResult(result)
                } else {
                    ThemedMessage.makeText(this@QRCodeToolActivity, R.string.qr_code_tool_invalid_qr, ThemedMessage.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                ThemedMessage.makeText(
                    this@QRCodeToolActivity,
                    R.string.qr_code_tool_pick_failed,
                    ThemedMessage.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Generate QR Code ───────────────────────────────────

    private fun generateFromEditor() {
        val content = richEditorView?.plainText?.trim().orEmpty()
        if (content.isEmpty()) {
            ThemedMessage.makeText(this, R.string.qr_code_tool_no_content, ThemedMessage.LENGTH_SHORT).show()
            return
        }
        if (viewModel.generateQrCode(content) == null) {
            ThemedMessage.makeText(this, R.string.qr_code_tool_content_too_long, ThemedMessage.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (viewModel.uiState.value.isScanning) stopScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
        stopBackgroundThread()
    }
}

// ── Screen (Compose) ──────────────────────────────────────────

// ponytail: angle-135 gradients from the XML drawables are approximated as top-left →
// bottom-right linear gradients; the stops are too dark to distinguish direction visually.
private val HeroGradient = Brush.linearGradient(listOf(Color(0x2E152033), Color(0x1E162B28)))
private val SurfacePanelGradient = Brush.linearGradient(listOf(Color(0xFF1D2A38), Color(0xFF18202C)))
private val PreviewFrameGradient = Brush.linearGradient(listOf(Color(0xFF17232E), Color(0xFF101824)))
private val QrButtonShape = RoundedCornerShape(18.dp)
private val SecondaryButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF22303F), Color(0xFF1B2531)))
private val SecondaryButtonGradientPressed = Brush.horizontalGradient(listOf(Color(0xFF304761), Color(0xFF25354A)))
private val SecondaryButtonStroke = Color(0x3300FF88)
private val SecondaryButtonStrokePressed = Color(0x5500FF88)

@Composable
internal fun QrCodeToolScreen(
    state: QRCodeToolUiState,
    onBack: () -> Unit,
    onScanToggle: () -> Unit,
    onGenerate: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromGalleryIdle: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEditorCreated: (RichTextEditorView) -> Unit,
    onEditorMessage: (CharSequence) -> Unit,
    onScanSurfaceCreated: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        QrToolHeader(onBack = onBack)
        NeonDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Content pane stays composed under the scan pane so the rich-text draft
            // (text + spans) survives scan round-trips, mirroring the XML GONE/VISIBLE pair.
            ContentPane(
                state = state,
                onEditorCreated = onEditorCreated,
                onEditorMessage = onEditorMessage,
                onSave = onSave,
                onShare = onShare,
                modifier = Modifier.fillMaxSize()
            )

            // Fully qualified: the outer ColumnScope receiver would otherwise capture
            // the ColumnScope.AnimatedVisibility overload, which is illegal here.
            androidx.compose.animation.AnimatedVisibility(
                visible = state.isScanning,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                ScanPane(
                    onPickFromGallery = onPickFromGallery,
                    onScanSurfaceCreated = onScanSurfaceCreated,
                    modifier = Modifier
                        .fillMaxSize()
                        // Consume taps on bare panel areas so the occluded content
                        // pane underneath cannot be touched while scanning.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isScanning) {
                val accent = MaterialTheme.colorScheme.primary
                QrActionButton(
                    text = stringResource(R.string.qr_code_tool_generate_button),
                    // The XML tinted the primary gradient with the live accent, flattening it.
                    container = Brush.horizontalGradient(listOf(accent, accent)),
                    pressedContainer = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.72f), accent.copy(alpha = 0.72f))),
                    stroke = accent.copy(alpha = 0x88 / 255f),
                    pressedStroke = accent.copy(alpha = 0x66 / 255f),
                    textColor = Color(0xFF08121A),
                    onClick = onGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_generate_qr")
                )
                QrActionButton(
                    text = stringResource(R.string.qr_code_tool_pick_gallery),
                    container = SecondaryButtonGradient,
                    pressedContainer = SecondaryButtonGradientPressed,
                    stroke = SecondaryButtonStroke,
                    pressedStroke = SecondaryButtonStrokePressed,
                    textColor = Color.White,
                    onClick = onPickFromGalleryIdle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_pick_gallery_idle")
                )
            }
            QrActionButton(
                text = stringResource(
                    if (state.isScanning) R.string.qr_code_tool_stop_scan
                    else R.string.qr_code_tool_scan_button
                ),
                container = if (state.isScanning) {
                    Brush.horizontalGradient(listOf(Color(0xFFD46262), Color(0xFF913737)))
                } else SecondaryButtonGradient,
                pressedContainer = if (state.isScanning) {
                    Brush.horizontalGradient(listOf(Color(0xFFE97E7E), Color(0xFFB84B4B)))
                } else SecondaryButtonGradientPressed,
                stroke = if (state.isScanning) Color(0x55FF9F9F) else SecondaryButtonStroke,
                pressedStroke = if (state.isScanning) Color(0x66FF9F9F) else SecondaryButtonStrokePressed,
                textColor = Color.White,
                onClick = onScanToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_scan_qr")
            )
        }
    }
}

@Composable
private fun QrToolHeader(onBack: () -> Unit) {
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
                contentDescription = stringResource(R.string.history_cancel),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.qr_code_tool_title),
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
private fun ContentPane(
    state: QRCodeToolUiState,
    onEditorCreated: (RichTextEditorView) -> Unit,
    onEditorMessage: (CharSequence) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .maxContentWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp)
        ) {
            HeroCard(state = state)
            PreviewCard(state = state, onSave = onSave, onShare = onShare)
            GenerateSectionHeader()
            EditorCard(
                onEditorCreated = onEditorCreated,
                onEditorMessage = onEditorMessage
            )
        }
    }
}

@Composable
private fun HeroCard(state: QRCodeToolUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(HeroGradient, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x3300FF88), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = stringResource(R.string.qr_code_tool_hero_badge), tinted = false)
            Spacer(modifier = Modifier.weight(1f))
            val hasPreview = state.generatedBitmap != null
            StatusPill(
                text = stringResource(
                    if (hasPreview) R.string.qr_code_tool_status_generated
                    else R.string.qr_code_tool_status_ready
                ),
                tinted = true,
                textColor = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = stringResource(R.string.qr_code_tool_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = stringResource(R.string.qr_code_tool_hero_description),
            color = TextBody,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HintCard(
                label = stringResource(R.string.qr_code_tool_scan_section),
                hint = stringResource(R.string.qr_code_tool_scan_card_hint),
                modifier = Modifier.weight(1f)
            )
            HintCard(
                label = stringResource(R.string.qr_code_tool_generate_section),
                hint = stringResource(R.string.qr_code_tool_generate_card_hint),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HintCard(label: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0x1A252A3A), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = hint,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun PreviewCard(state: QRCodeToolUiState, onSave: () -> Unit, onShare: () -> Unit) {
    val hasQr = state.generatedBitmap != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(Color(0x20252A3A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x2200FF88), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        StatusPill(text = stringResource(R.string.qr_code_tool_preview_section), tinted = false)

        Text(
            text = stringResource(
                if (hasQr) R.string.qr_code_tool_preview_generated_title
                else R.string.qr_code_tool_preview_idle_title
            ),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = if (hasQr) {
                stringResource(R.string.qr_code_tool_preview_generated_hint) + "\n" +
                    stringResource(R.string.qr_code_tool_save_hint)
            } else {
                stringResource(R.string.qr_code_tool_preview_idle_hint)
            },
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )

        val bitmap = state.generatedBitmap
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(260.dp)
                    .background(PreviewFrameGradient, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x3300FF88), RoundedCornerShape(18.dp))
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.qr_code_tool_qr_placeholder),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(220.dp)
                        .padding(16.dp)
                        .combinedClickable(onClick = {}, onLongClick = onSave)
                        .testTag("iv_qr_code")
                )

                QrIconOverlay(
                    iconRes = R.drawable.ic_qr_save,
                    contentDescription = stringResource(R.string.qr_code_tool_save_button),
                    onClick = onSave,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .testTag("btn_save_qr")
                )
                QrIconOverlay(
                    iconRes = android.R.drawable.ic_menu_share,
                    contentDescription = stringResource(R.string.qr_code_tool_share_button),
                    onClick = onShare,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .testTag("btn_share_qr")
                )
            }
        }
    }
}

@Composable
private fun QrIconOverlay(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(44.dp)
            .background(accent.copy(alpha = 0x18 / 255f), RoundedCornerShape(999.dp))
            .border(1.dp, accent.copy(alpha = 0x44 / 255f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun GenerateSectionHeader() {
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
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = stringResource(R.string.qr_code_tool_generate_section),
            color = Color(0x66FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun EditorCard(
    onEditorCreated: (RichTextEditorView) -> Unit,
    onEditorMessage: (CharSequence) -> Unit
) {
    // Captured once at factory time; the editor keeps it across later theme changes
    // (same staleness as the pre-migration accentArgb capture).
    val editorAccent = MaterialTheme.colorScheme.primary.toArgb()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color(0x20252A3A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x2200FF88), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.qr_code_tool_editor_helper),
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace
        )

        AndroidView(
            factory = { context ->
                RichTextEditorView(context).apply {
                    onMessage = onEditorMessage
                    setHint(context.getString(R.string.qr_code_tool_input_hint))
                    setEditorBackground(R.drawable.qr_tool_preview_frame_bg)
                    setEditorHeight((192 * context.resources.displayMetrics.density).toInt())
                    markdownActiveColor = editorAccent
                }.also(onEditorCreated)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun ScanPane(
    onPickFromGallery: () -> Unit,
    onScanSurfaceCreated: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val scanLineTransition = rememberInfiniteTransition(label = "scanLine")
    val scanLineProgress by scanLineTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    Box(modifier.padding(start = 14.dp, top = 16.dp, end = 14.dp, bottom = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePanelGradient, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x3300FF88), RoundedCornerShape(20.dp))
                .padding(14.dp)
                .onSizeChanged { panelHeightPx = it.height }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> SurfaceView(context).also(onScanSurfaceCreated) }
            )

            // Reticle; the XML tinted this flat drawable with the live accent.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(220.dp)
                    .background(accent.copy(alpha = 0x12 / 255f), RoundedCornerShape(24.dp))
                    .border(2.dp, accent.copy(alpha = 0xAA / 255f), RoundedCornerShape(24.dp))
            )

            // Sweeping scan line (was an ObjectAnimator on View#y).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(0, (scanLineProgress * (panelHeightPx - 2.dp.toPx())).roundToInt())
                    }
                    .width(220.dp)
                    .height(2.dp)
                    .background(accent.copy(alpha = 0x88 / 255f))
            )

            StatusPill(
                text = stringResource(R.string.qr_code_tool_scan_section),
                tinted = false,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusPill(
                    text = stringResource(R.string.qr_code_tool_scanning),
                    tinted = true,
                    textColor = accent,
                    fontSize = 11.sp
                )
                Text(
                    text = stringResource(R.string.qr_code_tool_scan_hint),
                    color = TextBright,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                QrActionButton(
                    text = stringResource(R.string.qr_code_tool_pick_gallery),
                    container = SecondaryButtonGradient,
                    pressedContainer = SecondaryButtonGradientPressed,
                    stroke = SecondaryButtonStroke,
                    pressedStroke = SecondaryButtonStrokePressed,
                    textColor = Color.White,
                    fontSize = 12.sp,
                    onClick = onPickFromGallery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("btn_pick_qr")
                )
            }
        }
    }
}

@Composable
private fun QrActionButton(
    text: String,
    container: Brush,
    pressedContainer: Brush,
    stroke: Color,
    pressedStroke: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .background(if (pressed) pressedContainer else container, QrButtonShape)
            .border(1.dp, if (pressed) pressedStroke else stroke, QrButtonShape)
            .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

/** Status pill; [tinted] mirrors the XML backgroundTintList accent treatment on flat shapes. */
@Composable
private fun StatusPill(
    text: String,
    tinted: Boolean,
    modifier: Modifier = Modifier,
    textColor: Color = TextBright,
    fontSize: TextUnit = 10.sp
) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        color = textColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(
                if (tinted) accent.copy(alpha = 0x18 / 255f) else Color(0x18253333),
                RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = if (tinted) accent.copy(alpha = 0x44 / 255f) else Color(0x4400FF88),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ── Scan-result bottom-sheet content (Compose in a BottomSheetDialog shell) ──

private val SheetTopShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
private val QrSheetBackground = Color(0xFF161A26)
private val StatusPillContainer = Color(0x18253333)
private val ResultCardContainer = Color(0x18FFFFFF)
private val ResultTextColor = Color(0xE6FFFFFF)
private val CopyButtonText = Color(0xFF08121A)

@Composable
private fun ScanResultSheetContent(
    result: String,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QrSheetBackground, SheetTopShape)
            .border(1.dp, outline, SheetTopShape)
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 5.dp)
                .background(outline, RoundedCornerShape(999.dp))
        )

        Text(
            text = stringResource(R.string.qr_code_tool_scan_result),
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 18.dp)
                .background(StatusPillContainer, RoundedCornerShape(999.dp))
                .border(1.dp, outline, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .background(ResultCardContainer, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.qr_code_tool_scan_result_label),
                color = accent.copy(alpha = 0x99 / 255f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            // Selectable result text (was android:textIsSelectable).
            SelectionContainer(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = result,
                    color = ResultTextColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        SheetButton(
            text = stringResource(R.string.qr_code_tool_copy_result),
            container = accent.copy(alpha = 0.6f),
            textColor = CopyButtonText,
            onClick = { onCopy(result) },
            modifier = Modifier
                .padding(top = 18.dp)
                .testTag("btn_copy_result")
        )
        SheetButton(
            text = stringResource(android.R.string.ok),
            container = accent.copy(alpha = 0x33 / 255f),
            textColor = Color.White,
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 10.dp)
                .testTag("btn_dismiss_result")
        )
    }
}

/** 50dp full-width action row; Button semantics replace the legacy accessibility delegate. */
@Composable
private fun SheetButton(
    text: String,
    container: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(container, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1118, widthDp = 412, heightDp = 1200)
@Composable
private fun QrCodeToolScreenPreview() {
    AircraftTheme {
        QrCodeToolScreen(
            state = QRCodeToolUiState(),
            onBack = {},
            onScanToggle = {},
            onGenerate = {},
            onPickFromGallery = {},
            onPickFromGalleryIdle = {},
            onSave = {},
            onShare = {},
            onEditorCreated = {},
            onEditorMessage = {},
            onScanSurfaceCreated = {}
        )
    }
}
