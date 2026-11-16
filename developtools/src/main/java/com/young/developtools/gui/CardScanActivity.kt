package com.young.developtools.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.young.developtools.R
import com.young.developtools.data.BankCard
import com.young.developtools.data.CardType
import com.young.developtools.data.IdCardEmblem
import com.young.developtools.data.IdCardPortrait
import com.young.developtools.data.ScanHint
import com.young.developtools.data.label
import com.young.developtools.ui.maxContentWidth
import com.young.developtools.ui.theme.AccentGreen
import com.young.developtools.ui.theme.AircraftTheme
import com.young.developtools.ui.theme.BackgroundDark
import com.young.developtools.ui.theme.TextSubtle
import com.young.developtools.utils.DeveloperMode
import com.young.developtools.utils.ImagePreprocessor
import com.young.developtools.utils.ImageQuality
import com.young.developtools.viewmodel.CardScanUiState
import com.young.developtools.viewmodel.CardScanViewModel
import com.young.developtools.viewmodel.ScanLens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import android.util.Size as CameraSize

/** Card long edge / short edge, shared by the on-screen frame and the ROI crop. */
private const val CARD_ASPECT = 1.586f

/** Centered share of the frame handed to OCR; the drawn frame is a little wider. */
private const val ROI_SCALE = 0.92f

/** OCR input width ceiling: enough for digits, cheap on low-end devices. */
private const val OCR_MAX_WIDTH = 1600

private const val FRAME_INTERVAL_MS = 180L

/**
 * Debug-only live card scanner: CameraX preview + ML Kit Text Recognition v2
 * (Latin for bank cards, bundled Chinese for ID cards), Luhn / GB 11643 checks
 * and cross-frame confirmation. Gallery images go through the same parsers.
 * On-device only — results are never stored or uploaded.
 */
class CardScanActivity : BaseAircraftActivity() {

    private lateinit var viewModel: CardScanViewModel
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
            // ML Kit does not apply EXIF orientation for bitmap input — a sideways photo
            // yields no text. Rotate the pixels to landscape ourselves (a card is wider
            // than tall) so OCR lines read correctly.
            val rotationDegrees = readExifRotationDegrees(uri)
            val landscape = ImagePreprocessor.rotateToLandscape(bitmap, rotationDegrees)
            // Ownership of the enhanced bitmap transfers to the ViewModel, which
            // recycles it after recognition — recycling here would race the async
            // ML Kit call and the native pipeline would fail to lock pixels.
            val enhanced = ImagePreprocessor.enhanceForOcr(landscape)
            if (landscape !== bitmap) landscape.recycle()
            viewModel.recognizeImage(InputImage.fromBitmap(enhanced, 0), enhanced)
        }.onFailure {
            viewModel.setError(R.string.card_scan_error)
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

    private fun readExifRotationDegrees(uri: Uri): Int {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    override fun initializeViewModel(savedInstanceState: Bundle?) {
        if (!DeveloperMode.isEnabled(this)) {
            finish()
            return
        }
        viewModel = ViewModelProvider(this, CardScanViewModel.Factory())[CardScanViewModel::class.java]
    }

    override fun initializeUI() {
        enableEdgeToEdge()
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        setContent {
            AircraftTheme {
                val uiState by viewModel.uiState.collectAsState()
                CardScanScreen(
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    onSelectType = viewModel::selectCardType,
                    onFrame = viewModel::recognizeFrame,
                    onHint = viewModel::setHint,
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
                    onCopy = ::copySensitiveText,
                    onRescan = { viewModel.resetScan() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun copySensitiveText(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // Mark sensitive so keyboard / synced clipboard clients hide it; clear after 60 s.
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Card scan result", text).apply {
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
private fun CardScanScreen(
    uiState: CardScanUiState,
    hasCameraPermission: Boolean,
    onSelectType: (CardType) -> Unit,
    onFrame: (Bitmap) -> Unit,
    onHint: (ScanHint?) -> Unit,
    onCameraError: () -> Unit,
    onSwitchLens: () -> Unit,
    onLensUnavailable: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPickImage: () -> Unit,
    onCopy: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    val scanning = hasCameraPermission && uiState.errorRes == null && !uiState.hasResult
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (scanning) {
            LivePreview(
                lens = uiState.lens,
                onFrame = onFrame,
                onHint = onHint,
                onLensUnavailable = onLensUnavailable,
                onCameraError = onCameraError,
                modifier = Modifier.fillMaxSize()
            )
            ScanFrameOverlay(modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            CardScanHeader(
                onBack = onBack,
                showLensSwitch = scanning,
                lens = uiState.lens,
                onSwitchLens = onSwitchLens
            )
            ModeSelector(
                selected = uiState.cardType,
                onSelect = onSelectType,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .maxContentWidth()
                .navigationBarsPadding()
                // Reserve the fixed Pick button's height (48.dp + 16.dp margin
                // + spacing) so content never slides underneath it.
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (scanning) {
                Text(
                    text = stringResource(uiState.cardType.hintRes()),
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                uiState.hint?.let { hint ->
                    Text(
                        text = stringResource(hint.hintRes()),
                        color = Color(0xFFFFC107),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("txt_scan_hint")
                    )
                }
            }

            Text(
                text = stringResource(R.string.card_scan_privacy_note),
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

            if (!hasCameraPermission && uiState.errorRes == null && !uiState.hasResult) {
                ErrorCard(
                    message = stringResource(R.string.card_scan_permission_hint),
                    primaryText = stringResource(R.string.camera_scan_grant),
                    onPrimary = onGrantPermission
                )
            }

            uiState.bankCard?.let { card ->
                BankResultCard(
                    card = card,
                    onCopy = { onCopy(card.number) },
                    onRescan = onRescan
                )
            }

            uiState.idPortrait?.let { portrait ->
                IdPortraitResultCard(
                    portrait = portrait,
                    onCopyId = { onCopy(portrait.idNumber) },
                    onRescan = onRescan
                )
            }

            uiState.idEmblem?.let { emblem ->
                IdEmblemResultCard(
                    emblem = emblem,
                    onCopyDetails = { onCopy("${emblem.issuer}\n${emblem.validity}".trim()) },
                    onRescan = onRescan
                )
            }

            if (uiState.fullText.isNotBlank()) {
                Text(
                    text = stringResource(R.string.card_scan_full_text_label),
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

        // Fixed outside the content Column so it never moves with
        // result/error/full-text changes; stays glued to the bottom.
        Button(
            onClick = onPickImage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .maxContentWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(48.dp)
                .testTag("btn_pick_card_image"),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen.copy(alpha = 0.18f),
                contentColor = AccentGreen
            )
        ) {
            Text(stringResource(R.string.card_scan_pick_image), fontSize = 13.sp)
        }
    }
}

private fun CardType.hintRes(): Int = when (this) {
    CardType.BANK_CARD -> R.string.card_scan_hint_bank
    CardType.ID_CARD -> R.string.card_scan_hint_id
}

private fun ScanHint.hintRes(): Int = when (this) {
    ScanHint.BLUR -> R.string.card_scan_hint_blur
    ScanHint.DARK -> R.string.card_scan_hint_dark
    ScanHint.BRIGHT -> R.string.card_scan_hint_bright
    ScanHint.GLARE -> R.string.card_scan_hint_glare
}

@Composable
private fun ModeSelector(
    selected: CardType,
    onSelect: (CardType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(BackgroundDark.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeChip(
            labelRes = R.string.card_scan_mode_bank,
            selected = selected == CardType.BANK_CARD,
            testTag = "btn_mode_bank",
            onClick = { onSelect(CardType.BANK_CARD) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            labelRes = R.string.card_scan_mode_id,
            selected = selected == CardType.ID_CARD,
            testTag = "btn_mode_id",
            onClick = { onSelect(CardType.ID_CARD) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeChip(
    labelRes: Int,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(labelRes)
    Text(
        text = label,
        color = if (selected) BackgroundDark else AccentGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) AccentGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
            .testTag(testTag),
        textAlign = TextAlign.Center
    )
}

/** Dim mask plus a card-shaped frame; the analyzer crops the same centered area. */
@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    val frameColor = AccentGreen
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.86f
        val frameHeight = frameWidth / CARD_ASPECT
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, size = Size(size.width, top.coerceAtLeast(0f)))
        drawRect(
            dim,
            topLeft = Offset(0f, top + frameHeight),
            size = Size(size.width, (size.height - top - frameHeight).coerceAtLeast(0f))
        )
        drawRect(dim, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), frameHeight))
        drawRect(
            dim,
            topLeft = Offset(left + frameWidth, top),
            size = Size((size.width - left - frameWidth).coerceAtLeast(0f), frameHeight)
        )
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun CardScanHeader(
    onBack: () -> Unit,
    showLensSwitch: Boolean,
    lens: ScanLens,
    onSwitchLens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backDescription = stringResource(R.string.card_scan_back)
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
            text = stringResource(R.string.card_scan_title),
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

/**
 * Single shared executor for frame analysis. Never shut down: ML Kit delivers
 * task completions/cancellations asynchronously, potentially after
 * `TextRecognizer.close()`, and a terminated executor would reject that
 * dispatch with `RejectedExecutionException` and crash the process.
 */
private object CardScanAnalyzerExecutor {
    val instance: ExecutorService = Executors.newSingleThreadExecutor()
}

/**
 * Crops every analyzed frame to the centered card region before OCR, so ML Kit
 * never spends time on the background and the digits occupy more pixels.
 *
 * Frames are throttled and only one capture is in flight at a time; the
 * ViewModel independently drops frames while a recognition is running.
 */
private class CardFrameAnalyzer(
    private val onFrame: (Bitmap) -> Unit,
    private val onHint: (ScanHint?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val capturing = AtomicBoolean(false)
    private var lastFrameAt = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS || !capturing.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastFrameAt = now
        try {
            val bitmap = image.toCroppedCardBitmap()
            onHint(ImageQuality.check(bitmap))
            onFrame(bitmap)
        } catch (_: Exception) {
            // A malformed frame must not tear down the analysis pipeline.
        } finally {
            image.close()
            capturing.set(false)
        }
    }
}

/**
 * Rotates to upright, then crops a centered card-aspect region. Returns a new
 * bitmap owned by the caller; the intermediate image is recycled.
 */
private fun ImageProxy.toCroppedCardBitmap(): Bitmap {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) {
        source
    } else {
        Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        ).also { if (it !== source) source.recycle() }
    }
    val cropWidth: Int
    val cropHeight: Int
    if (upright.width / upright.height.toFloat() > CARD_ASPECT) {
        cropHeight = (upright.height * ROI_SCALE).toInt()
        cropWidth = (cropHeight * CARD_ASPECT).toInt()
    } else {
        cropWidth = (upright.width * ROI_SCALE).toInt()
        cropHeight = (cropWidth / CARD_ASPECT).toInt()
    }
    val left = (upright.width - cropWidth) / 2
    val top = (upright.height - cropHeight) / 2
    val cropped = Bitmap.createBitmap(upright, left, top, cropWidth, cropHeight)
    if (cropped !== upright) upright.recycle()
    if (cropped.width <= OCR_MAX_WIDTH) return cropped
    val scaledHeight = (cropped.height * OCR_MAX_WIDTH.toFloat() / cropped.width).toInt()
    val scaled = cropped.scale(OCR_MAX_WIDTH, scaledHeight)
    cropped.recycle()
    return scaled
}

@Composable
private fun LivePreview(
    lens: ScanLens,
    onFrame: (Bitmap) -> Unit,
    onHint: (ScanHint?) -> Unit,
    onLensUnavailable: () -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val currentOnFrame = rememberUpdatedState(onFrame)
    val currentOnHint = rememberUpdatedState(onHint)
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }
    val analyzerExecutor = CardScanAnalyzerExecutor.instance

    DisposableEffect(lens) {
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
                // Default analysis resolution is too low for small card glyphs:
                // pin it to full 1080p so each stroke survives the crop.
                val analysisSize = CameraSize(1920, 1080)
                setImageAnalysisResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                analysisSize,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                setImageAnalysisAnalyzer(
                    analyzerExecutor,
                    CardFrameAnalyzer(
                        onFrame = { bitmap -> currentOnFrame.value(bitmap) },
                        onHint = { hint -> currentOnHint.value(hint) }
                    )
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
        onDispose {
            controller?.unbind()
            previewView.controller = null
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })

    DisposableEffect(lifecycleOwner) {
        onDispose {
            previewView.controller = null
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
private fun ResultCardContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .border(1.dp, AccentGreen.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun ResultField(labelRes: Int, value: String, highlight: Boolean = false) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(labelRes),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.36f)
        )
        SelectionContainer(modifier = Modifier.weight(0.64f)) {
            Text(
                text = value,
                color = if (highlight) AccentGreen else TextSubtle,
                fontSize = if (highlight) 15.sp else 13.sp,
                lineHeight = if (highlight) 19.sp else 17.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BankResultCard(
    card: BankCard,
    onCopy: () -> Unit,
    onRescan: () -> Unit
) {
    ResultCardContainer {
        Text(
            text = stringResource(R.string.card_scan_bank_number_label),
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
        ResultButtons(
            primaryText = stringResource(R.string.card_scan_copy_number),
            primaryTag = "btn_copy_card_number",
            onPrimary = onCopy,
            onRescan = onRescan
        )
    }
}

@Composable
private fun IdPortraitResultCard(
    portrait: IdCardPortrait,
    onCopyId: () -> Unit,
    onRescan: () -> Unit
) {
    ResultCardContainer {
        Text(
            text = stringResource(R.string.card_scan_id_label),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        ResultField(R.string.card_scan_field_name, portrait.name)
        ResultField(R.string.card_scan_field_gender, portrait.gender)
        ResultField(R.string.card_scan_field_ethnicity, portrait.ethnicity)
        ResultField(R.string.card_scan_field_birth, portrait.birth)
        ResultField(R.string.card_scan_field_address, portrait.address)
        ResultField(R.string.card_scan_field_id_number, portrait.idNumber, highlight = true)
        ResultButtons(
            primaryText = stringResource(R.string.card_scan_copy_id),
            primaryTag = "btn_copy_id_number",
            onPrimary = onCopyId,
            onRescan = onRescan
        )
    }
}

@Composable
private fun IdEmblemResultCard(
    emblem: IdCardEmblem,
    onCopyDetails: () -> Unit,
    onRescan: () -> Unit
) {
    ResultCardContainer {
        Text(
            text = stringResource(R.string.card_scan_id_label),
            color = AccentGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        ResultField(R.string.card_scan_field_issuer, emblem.issuer)
        ResultField(R.string.card_scan_field_validity, emblem.validity)
        ResultButtons(
            primaryText = stringResource(R.string.card_scan_copy_details),
            primaryTag = "btn_copy_id_details",
            onPrimary = onCopyDetails,
            onRescan = onRescan
        )
    }
}

@Composable
private fun ResultButtons(
    primaryText: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    onRescan: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag(primaryTag),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = BackgroundDark
            )
        ) {
            Text(primaryText, fontSize = 13.sp)
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
            Text(stringResource(R.string.card_scan_rescan), fontSize = 13.sp)
        }
    }
}
