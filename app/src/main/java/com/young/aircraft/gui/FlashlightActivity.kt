package com.young.aircraft.gui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.viewmodel.FlashlightUiState
import com.young.aircraft.viewmodel.FlashlightViewModel
import kotlin.math.roundToInt

class FlashlightActivity : AppCompatActivity() {

    private lateinit var viewModel: FlashlightViewModel
    private var hasCameraPermission by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(this, R.string.flashlight_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge with permanently dark system bars to match the tactical theme.
        // Use SystemBarStyle.dark on both bars so the system always renders light icons,
        // regardless of the device's day/night setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this)[FlashlightViewModel::class.java]
        hasCameraPermission = hasCameraPermission()
        requestCameraPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()
                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let {
                        Toast.makeText(this@FlashlightActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                }
                FlashlightScreen(
                    uiState = uiState,
                    hasCameraPermission = hasCameraPermission,
                    onBack = { finish() },
                    onToggleFlashlight = viewModel::toggleFlashlight,
                    onToggleSos = viewModel::toggleSosMode,
                    onSosFrequencyChange = viewModel::setSosFrequency,
                    onBrightnessChange = viewModel::setBrightness,
                    onRequestPermission = ::requestCameraPermissionIfNeeded
                )
            }
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private val FlashAccent = Color(0xFF00FF88)
private val FlashBackground = Color(0xFF0F1118)
private val FlashSurface = Color(0x20252A3A)
private val FlashSurfaceHigh = Color(0xFF161A26)
private val FlashHeader = Color(0xFF161A26)
private val FlashText = Color(0xFFD8E0EF)
private val FlashSubText = Color(0xFFAAB4C8)
private val FlashBorder = Color(0x3300FF88)
private val FlashCritical = Color(0xFFFF6F7E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashlightScreen(
    uiState: FlashlightUiState,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleSos: (Boolean) -> Unit,
    onSosFrequencyChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onRequestPermission: () -> Unit
) {
    Scaffold(
        containerColor = FlashBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.flashlight_title),
                            color = FlashAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.25.sp
                        )
                        Text(
                            text = stringResource(
                                if (uiState.isFlashAvailable) R.string.flashlight_available
                                else R.string.flashlight_unavailable
                            ),
                            color = FlashSubText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_header_back),
                            contentDescription = stringResource(R.string.history_back),
                            tint = FlashAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = FlashHeader),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("flashlight_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                FlashStatusPanel(uiState = uiState, hasCameraPermission = hasCameraPermission)
            }
            item {
                TorchHero(isOn = uiState.isOn, isSosMode = uiState.isSosMode)
            }
            item {
                if (!hasCameraPermission || !uiState.isFlashAvailable) {
                    PermissionCard(onRequestPermission = onRequestPermission)
                }
            }
            item {
                ToggleCard(
                    uiState = uiState,
                    controlsEnabled = hasCameraPermission,
                    onToggleFlashlight = onToggleFlashlight
                )
            }
            item {
                SosCard(
                    uiState = uiState,
                    controlsEnabled = hasCameraPermission,
                    onToggleSos = onToggleSos,
                    onSosFrequencyChange = onSosFrequencyChange
                )
            }
            item {
                BrightnessCard(
                    uiState = uiState,
                    controlsEnabled = hasCameraPermission,
                    onBrightnessChange = onBrightnessChange
                )
            }
        }
    }
}

@Composable
private fun FlashStatusPanel(
    uiState: FlashlightUiState,
    hasCameraPermission: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FlashSurfaceHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, FlashBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    label = stringResource(
                        if (uiState.isOn) R.string.flashlight_toggle_on else R.string.flashlight_toggle_off
                    ),
                    active = uiState.isOn,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = stringResource(R.string.flashlight_sos_button),
                    active = uiState.isSosMode,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    label = stringResource(
                        if (hasCameraPermission) R.string.flashlight_permission_ready
                        else R.string.flashlight_permission_title
                    ),
                    active = hasCameraPermission,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = stringResource(
                        if (uiState.maxBrightnessLevel > 1) R.string.flashlight_brightness_multilevel
                        else R.string.flashlight_brightness_binary
                    ),
                    active = uiState.maxBrightnessLevel > 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 36.dp),
        color = if (active) FlashAccent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (active) FlashAccent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) FlashAccent else FlashSubText.copy(alpha = 0.5f))
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                color = if (active) FlashAccent else FlashSubText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TorchHero(
    isOn: Boolean,
    isSosMode: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flashlight_pulse_loop")
    val activePulse by infiniteTransition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSosMode) 520 else 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashlight_active_pulse"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isOn) activePulse else 0.18f,
        label = "flashlight_glow_alpha"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp),
        color = FlashSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, FlashBorder),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            FlashAccent.copy(alpha = if (isOn) 0.13f else 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(204.dp)) {
                val radius = size.minDimension / 2f
                drawCircle(FlashAccent.copy(alpha = glowAlpha * 0.18f), radius = radius)
                drawCircle(FlashAccent.copy(alpha = glowAlpha * 0.30f), radius = radius * 0.72f)
                drawCircle(
                    color = FlashAccent.copy(alpha = if (isOn) glowAlpha else 0.28f),
                    radius = radius * 0.42f
                )
                drawCircle(
                    color = FlashAccent.copy(alpha = 0.70f),
                    radius = radius * 0.86f,
                    style = Stroke(width = 3.dp.toPx())
                )
                // Beam stops at 86% of canvas height to leave clear space for the label
                // pill at the bottom of the parent Box (prevents the beam line from
                // bleeding through the pill's semi-transparent background).
                drawLine(
                    color = FlashAccent.copy(alpha = if (isOn) glowAlpha * 0.65f else 0.12f),
                    start = center.copy(y = center.y + radius * 0.44f),
                    end = center.copy(y = size.height * 0.86f),
                    strokeWidth = 9.dp.toPx()
                )
            }
            Text(
                text = if (isSosMode) stringResource(R.string.flashlight_sos_button)
                else stringResource(R.string.flashlight_toggle_title),
                color = if (isOn) FlashAccent else FlashSubText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlashSurfaceHigh)
                    .border(1.dp, FlashBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    ControlCard(borderColor = FlashCritical.copy(alpha = 0.42f)) {
        Text(
            text = stringResource(R.string.flashlight_permission_title),
            color = FlashCritical,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = stringResource(R.string.flashlight_permission_message),
            color = FlashSubText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .padding(top = 12.dp)
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FlashAccent, contentColor = Color(0xFF07120D))
        ) {
            Text(text = stringResource(R.string.flashlight_permission_button), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ToggleCard(
    uiState: FlashlightUiState,
    controlsEnabled: Boolean,
    onToggleFlashlight: (Boolean) -> Unit
) {
    ControlCard {
        SectionHeader(
            title = stringResource(R.string.flashlight_toggle_title),
            value = stringResource(
                if (uiState.isOn) R.string.flashlight_toggle_on else R.string.flashlight_toggle_off
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.flashlight_summary),
                    color = FlashText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Switch(
                checked = uiState.isOn,
                enabled = controlsEnabled && uiState.isFlashAvailable,
                onCheckedChange = onToggleFlashlight,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = FlashAccent,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun SosCard(
    uiState: FlashlightUiState,
    controlsEnabled: Boolean,
    onToggleSos: (Boolean) -> Unit,
    onSosFrequencyChange: (Float) -> Unit
) {
    ControlCard {
        SectionHeader(
            title = stringResource(R.string.flashlight_sos_title),
            value = stringResource(R.string.flashlight_ms_per_unit, uiState.sosUnitMs.roundToInt())
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.flashlight_sos_summary),
                    color = FlashSubText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            OutlinedButton(
                enabled = controlsEnabled && uiState.isFlashAvailable,
                onClick = { onToggleSos(!uiState.isSosMode) },
                border = BorderStroke(1.dp, if (uiState.isSosMode) FlashAccent else FlashBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (uiState.isSosMode) FlashAccent else FlashText
                )
            ) {
                Text(text = stringResource(R.string.flashlight_sos_button), fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SliderTrackLabel(title = stringResource(R.string.flashlight_blink_speed))
        Slider(
            value = uiState.sosUnitMs,
            enabled = controlsEnabled && uiState.isFlashAvailable,
            onValueChange = onSosFrequencyChange,
            valueRange = FlashlightViewModel.SOS_MIN_UNIT_MS..FlashlightViewModel.SOS_MAX_UNIT_MS,
            steps = FlashlightViewModel.SOS_STEPS,
            colors = SliderDefaults.colors(
                activeTrackColor = FlashAccent,
                thumbColor = FlashAccent
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.flashlight_fast), color = FlashSubText, fontSize = 11.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = stringResource(R.string.flashlight_slow), color = FlashSubText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BrightnessCard(
    uiState: FlashlightUiState,
    controlsEnabled: Boolean,
    onBrightnessChange: (Float) -> Unit
) {
    ControlCard {
        SectionHeader(
            title = stringResource(R.string.flashlight_brightness_title),
            value = stringResource(R.string.flashlight_percent, (uiState.brightnessLevel * 100).roundToInt())
        )
        Slider(
            value = uiState.brightnessLevel,
            enabled = controlsEnabled && uiState.isFlashAvailable,
            onValueChange = onBrightnessChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                activeTrackColor = FlashAccent,
                inactiveTrackColor = Color(0xFF555555),
                thumbColor = FlashAccent
            )
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || uiState.maxBrightnessLevel <= 1) {
            Text(
                text = stringResource(R.string.flashlight_android_13_note),
                color = FlashSubText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1A00FF88))
                    .border(1.dp, FlashBorder, RoundedCornerShape(8.dp))
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(FlashAccent)
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp),
            color = FlashText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = FlashAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SliderTrackLabel(title: String) {
    Text(
        text = title,
        color = FlashSubText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun ControlCard(
    borderColor: Color = FlashBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = FlashSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
    HorizontalDivider(color = Color.Transparent)
}
