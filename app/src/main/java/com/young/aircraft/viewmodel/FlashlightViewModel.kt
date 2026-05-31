package com.young.aircraft.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.aircraft.service.FlashlightService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class FlashlightUiState(
    val isOn: Boolean = false,
    val isSosMode: Boolean = false,
    val sosUnitMs: Float = DEFAULT_SOS_UNIT_MS,
    val brightnessLevel: Float = 1f,
    val maxBrightnessLevel: Int = 1,
    val isFlashAvailable: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        const val DEFAULT_SOS_UNIT_MS = 150f
    }
}

data class FlashlightPulse(
    val torchOn: Boolean,
    val units: Int
)

/**
 * Holds UI state for the flashlight screen and forwards intent commands to
 * [FlashlightService] (the foreground service that actually owns the torch).
 *
 * State sync is one-way: torch on/off is observed via [CameraManager.TorchCallback]
 * (which fires regardless of who toggled the torch — service, system, or another app),
 * and SOS state is observed via [FlashlightService.isSosRunning]. The ViewModel never
 * calls `cameraManager.setTorchMode` directly.
 */
class FlashlightViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraManager =
        application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val cameraId: String? = findFlashCameraId()

    private val _uiState = MutableStateFlow(
        FlashlightUiState(isFlashAvailable = cameraId != null)
    )
    val uiState: StateFlow<FlashlightUiState> = _uiState.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == this@FlashlightViewModel.cameraId) {
                _uiState.update { it.copy(isOn = enabled, errorMessage = null) }
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == this@FlashlightViewModel.cameraId) {
                _uiState.update { it.copy(isOn = false, errorMessage = "Torch is unavailable") }
            }
        }
    }

    init {
        runCatching {
            cameraManager.registerTorchCallback(torchCallback, callbackHandler)
        }
        initBrightness()
        // Mirror the service's SOS state so the UI reflects whether the foreground
        // service is currently broadcasting the SOS pattern.
        viewModelScope.launch {
            FlashlightService.isSosRunning.collect { running ->
                _uiState.update { it.copy(isSosMode = running) }
            }
        }
    }

    fun toggleFlashlight(enabled: Boolean) {
        sendCommand(
            if (enabled) FlashlightService.ACTION_TORCH_ON
            else FlashlightService.ACTION_TORCH_OFF
        )
    }

    fun toggleSosMode(enabled: Boolean) {
        sendCommand(
            if (enabled) FlashlightService.ACTION_SOS_ON
            else FlashlightService.ACTION_SOS_OFF
        )
    }

    fun setSosFrequency(value: Float) {
        val coerced = value.coerceIn(SOS_MIN_UNIT_MS, SOS_MAX_UNIT_MS)
        _uiState.update { it.copy(sosUnitMs = coerced) }
        // If SOS is already running, push the new pacing into the service so subsequent pulses
        // pick it up. Re-issuing ACTION_SOS_ON is a no-op for cancellation but updates the unit.
        if (uiState.value.isSosMode) {
            sendCommand(FlashlightService.ACTION_SOS_ON)
        }
    }

    fun setBrightness(level: Float) {
        val coerced = level.coerceIn(0f, 1f)
        _uiState.update { it.copy(brightnessLevel = coerced) }
        // Only push to the service if the torch is currently on — otherwise we'd start it.
        if (uiState.value.isOn && !uiState.value.isSosMode) {
            sendCommand(FlashlightService.ACTION_TORCH_ON)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun sendCommand(action: String) {
        val app = getApplication<Application>()
        val intent = Intent(app, FlashlightService::class.java).apply {
            this.action = action
            putExtra(FlashlightService.EXTRA_BRIGHTNESS, uiState.value.brightnessLevel)
            putExtra(FlashlightService.EXTRA_SOS_UNIT_MS, uiState.value.sosUnitMs.toLong())
        }
        runCatching {
            // ACTION_TORCH_OFF and ACTION_SOS_OFF still go through startForegroundService:
            // the service starts (or is already up), promotes to foreground, then stops itself.
            // This avoids "Context.startForegroundService did not call startForeground" crashes.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    private fun initBrightness() {
        val id = cameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val maxLevel = runCatching {
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                    ?: 1
            }.getOrDefault(1)
            _uiState.update { it.copy(maxBrightnessLevel = maxLevel.coerceAtLeast(1)) }
        }
    }

    private fun findFlashCameraId(): String? =
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

    override fun onCleared() {
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        super.onCleared()
    }

    companion object {
        const val SOS_MIN_UNIT_MS = 50f
        const val SOS_MAX_UNIT_MS = 500f
        const val SOS_STEPS = 8
        const val DEFAULT_SOS_UNIT_MS_LONG: Long = 150L

        val SOS_PATTERN = listOf(
            FlashlightPulse(true, 1), FlashlightPulse(false, 1),
            FlashlightPulse(true, 1), FlashlightPulse(false, 1),
            FlashlightPulse(true, 1), FlashlightPulse(false, 3),
            FlashlightPulse(true, 3), FlashlightPulse(false, 1),
            FlashlightPulse(true, 3), FlashlightPulse(false, 1),
            FlashlightPulse(true, 3), FlashlightPulse(false, 3),
            FlashlightPulse(true, 1), FlashlightPulse(false, 1),
            FlashlightPulse(true, 1), FlashlightPulse(false, 1),
            FlashlightPulse(true, 1)
        )

        fun brightnessToStrengthLevel(level: Float, maxBrightnessLevel: Int): Int {
            if (maxBrightnessLevel <= 1) return 1
            return (level.coerceIn(0f, 1f) * maxBrightnessLevel)
                .roundToInt()
                .coerceIn(1, maxBrightnessLevel)
        }
    }
}
