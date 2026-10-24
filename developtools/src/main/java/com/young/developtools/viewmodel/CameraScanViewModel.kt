package com.young.developtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lens the live scanner should bind to. Kept independent of CameraX types so the
 * state machine stays unit-testable.
 */
enum class ScanLens { BACK, FRONT }

/**
 * UI state for the CameraX live QR scan screen (debug-only developer tool).
 */
data class CameraScanUiState(
    val result: String? = null,
    val errorRes: Int? = null,
    val lens: ScanLens = ScanLens.BACK
)

class CameraScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraScanUiState())
    val uiState: StateFlow<CameraScanUiState> = _uiState.asStateFlow()

    /** First hit wins; the analyzer keeps feeding until the reader explicitly resets. */
    fun onBarcodeDetected(raw: String) {
        if (raw.isBlank() || _uiState.value.result != null) return
        _uiState.update { it.copy(result = raw, errorRes = null) }
    }

    fun resetScan() {
        _uiState.update { it.copy(result = null, errorRes = null) }
    }

    fun setError(res: Int?) {
        _uiState.update { it.copy(errorRes = res) }
    }

    fun toggleLens() {
        _uiState.update {
            it.copy(lens = if (it.lens == ScanLens.FRONT) ScanLens.BACK else ScanLens.FRONT)
        }
    }

    /** Used when the front lens is absent or fails to bind, so the preview recovers. */
    fun useRearLens() {
        _uiState.update { it.copy(lens = ScanLens.BACK) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraScanViewModel() as T
        }
    }
}
