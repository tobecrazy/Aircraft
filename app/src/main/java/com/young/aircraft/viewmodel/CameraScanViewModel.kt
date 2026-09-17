package com.young.aircraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the CameraX live QR scan screen (debug-only developer tool).
 */
data class CameraScanUiState(
    val result: String? = null,
    val errorRes: Int? = null
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

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraScanViewModel() as T
        }
    }
}
