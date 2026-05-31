package com.young.aircraft.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

class FlashlightViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraManager =
        application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val cameraId: String? = findFlashCameraId()
    private var sosJob: Job? = null

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
    }

    fun toggleFlashlight(enabled: Boolean) {
        sosJob?.cancel()
        sosJob = null
        _uiState.update { it.copy(isSosMode = false) }
        setTorchDirect(enabled)
    }

    fun toggleSosMode(enabled: Boolean) {
        sosJob?.cancel()
        sosJob = null
        _uiState.update { it.copy(isSosMode = enabled) }

        if (!enabled) {
            setTorchDirect(false)
            return
        }

        sosJob = viewModelScope.launch {
            while (isActive) {
                for (pulse in SOS_PATTERN) {
                    setTorchDirect(pulse.torchOn)
                    delay(pulse.units * uiState.value.sosUnitMs.toLong())
                }
                setTorchDirect(false)
                delay(SOS_WORD_GAP_UNITS * uiState.value.sosUnitMs.toLong())
            }
        }
    }

    fun setSosFrequency(value: Float) {
        _uiState.update { it.copy(sosUnitMs = value.coerceIn(SOS_MIN_UNIT_MS, SOS_MAX_UNIT_MS)) }
    }

    fun setBrightness(level: Float) {
        val coercedLevel = level.coerceIn(0f, 1f)
        _uiState.update { it.copy(brightnessLevel = coercedLevel) }
        if (!uiState.value.isOn) return
        setTorchDirect(true)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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

    private fun setTorchDirect(enabled: Boolean) {
        val id = cameraId
        if (id == null) {
            _uiState.update {
                it.copy(isOn = false, isFlashAvailable = false, errorMessage = "No camera flash found")
            }
            return
        }

        val result = runCatching {
            if (
                enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                uiState.value.maxBrightnessLevel > 1
            ) {
                cameraManager.turnOnTorchWithStrengthLevel(
                    id,
                    brightnessToStrengthLevel(
                        uiState.value.brightnessLevel,
                        uiState.value.maxBrightnessLevel
                    )
                )
            } else {
                cameraManager.setTorchMode(id, enabled)
            }
        }

        _uiState.update {
            it.copy(
                isOn = if (result.isSuccess) enabled else false,
                errorMessage = result.exceptionOrNull()?.message
            )
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
        sosJob?.cancel()
        setTorchDirect(false)
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        super.onCleared()
    }

    companion object {
        const val SOS_MIN_UNIT_MS = 50f
        const val SOS_MAX_UNIT_MS = 500f
        const val SOS_STEPS = 8
        private const val SOS_WORD_GAP_UNITS = 7

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
