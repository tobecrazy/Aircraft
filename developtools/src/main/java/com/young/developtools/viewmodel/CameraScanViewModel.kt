package com.young.developtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.developtools.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Which detector the live preview feeds. */
enum class CameraScanMode { QR, CARD }

/** Which camera lens the preview uses. */
enum class CameraLens { BACK, FRONT }

/** True when card scanning can run, with or without the on-device model. */
private fun CardModelStatus.canScan(): Boolean =
    this == CardModelStatus.AVAILABLE || this == CardModelStatus.LOCAL_ONLY

/**
 * UI state for the CameraX live scan screen (debug-only developer tool).
 *
 * Card-number state stays in memory only: no frame, OCR text, PAN, or model
 * response is written to Room, prefs, request history, or logs.
 */
data class CameraScanUiState(
    val mode: CameraScanMode = CameraScanMode.QR,
    val lensFacing: CameraLens = CameraLens.BACK,
    val result: String? = null,
    val errorRes: Int? = null,
    val cardModelStatus: CardModelStatus = CardModelStatus.CHECKING,
    val cardDownloadProgress: Int? = null,
    val cardModelErrorRes: Int? = null,
    val cardNumber: String? = null,
    val cardErrorRes: Int? = null,
    val isCardAnalyzing: Boolean = false
)

class CameraScanViewModel(
    private val cardRecognizer: CardNumberRecognizer = MlKitCardNumberRecognizer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraScanUiState())
    val uiState: StateFlow<CameraScanUiState> = _uiState.asStateFlow()

    /** Drops overlapping OCR/generation work instead of queueing frames. */
    private val cardAnalysisInFlight = AtomicBoolean(false)
    private var availabilityJob: Job? = null
    private var downloadJob: Job? = null

    /** Bumped on every rescan so late results from an older frame are discarded. */
    private var cardScanGeneration = 0

    /** First hit wins; the analyzer keeps feeding until the reader explicitly resets. */
    fun onBarcodeDetected(raw: String) {
        if (raw.isBlank() || _uiState.value.result != null) return
        _uiState.update { it.copy(result = raw, errorRes = null) }
    }

    fun resetScan() {
        _uiState.update { it.copy(result = null, errorRes = null) }
    }

    fun setMode(mode: CameraScanMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode) }
        if (mode == CameraScanMode.CARD) {
            val status = _uiState.value.cardModelStatus
            if (!status.canScan()) {
                checkCardModelAvailability()
            }
        }
    }

    /** Switches between the rear and front cameras without touching the current result. */
    fun toggleLens() {
        _uiState.update {
            it.copy(
                lensFacing = if (it.lensFacing == CameraLens.BACK) {
                    CameraLens.FRONT
                } else {
                    CameraLens.BACK
                }
            )
        }
    }

    /** Re-checks FeatureStatus and structured-output support; never falls back to the cloud. */
    fun checkCardModelAvailability() {
        if (availabilityJob?.isActive == true || downloadJob?.isActive == true) return
        _uiState.update {
            it.copy(
                cardModelStatus = CardModelStatus.CHECKING,
                cardModelErrorRes = null,
                cardDownloadProgress = null
            )
        }
        availabilityJob = viewModelScope.launch {
            val status = try {
                cardRecognizer.checkAvailability()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CardModelStatus.LOCAL_ONLY
            }
            if (!status.canScan()) {
                // Losing the model invalidates any result or error already on screen.
                cardScanGeneration++
            }
            applyAvailability(status)
        }
    }

    /** Applies a freshly checked model status and drops stale card state when it is unusable. */
    private fun applyAvailability(status: CardModelStatus) {
        _uiState.update {
            if (status == CardModelStatus.AVAILABLE) {
                it.copy(cardModelStatus = status, cardDownloadProgress = null)
            } else {
                it.copy(
                    cardModelStatus = status,
                    cardDownloadProgress = null,
                    cardNumber = null,
                    cardErrorRes = null,
                    isCardAnalyzing = false
                )
            }
        }
    }

    fun downloadCardModel() {
        if (downloadJob?.isActive == true) return
        _uiState.update {
            it.copy(
                cardModelStatus = CardModelStatus.DOWNLOADING,
                cardDownloadProgress = 0,
                cardModelErrorRes = null
            )
        }
        downloadJob = viewModelScope.launch {
            val downloaded = try {
                cardRecognizer.download { progress ->
                    _uiState.update { it.copy(cardDownloadProgress = progress) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (downloaded) {
                availabilityJob = viewModelScope.launch {
                    val status = try {
                        cardRecognizer.checkAvailability()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CardModelStatus.LOCAL_ONLY
                    }
                    if (!status.canScan()) {
                        cardScanGeneration++
                    }
                    applyAvailability(status)
                }
            } else {
                cardScanGeneration++
                _uiState.update {
                    it.copy(
                        cardModelStatus = CardModelStatus.DOWNLOAD_REQUIRED,
                        cardDownloadProgress = null,
                        cardModelErrorRes = R.string.camera_scan_card_download_failed,
                        cardNumber = null,
                        cardErrorRes = null,
                        isCardAnalyzing = false
                    )
                }
            }
        }
    }

    /**
     * Feeds OCR text to the model. Frames are dropped while a request is in
     * flight or once a result/error locks the screen.
     */
    fun onCardFrameText(ocrText: String) {
        val state = _uiState.value
        if (state.mode != CameraScanMode.CARD) return
        if (!state.cardModelStatus.canScan()) return
        if (state.cardNumber != null || state.cardErrorRes != null) return
        if (ocrText.isBlank()) return
        if (!cardAnalysisInFlight.compareAndSet(false, true)) return

        val generation = cardScanGeneration
        _uiState.update { it.copy(isCardAnalyzing = true) }
        viewModelScope.launch {
            try {
                val candidate = if (state.cardModelStatus == CardModelStatus.LOCAL_ONLY) {
                    CardNumberValidator.extract(ocrText)
                } else {
                    cardRecognizer.extractCandidate(ocrText)
                }
                val validated = CardNumberValidator.validate(ocrText, candidate)
                if (generation != cardScanGeneration) return@launch
                _uiState.update { current ->
                    if (validated != null) {
                        current.copy(
                            cardNumber = validated,
                            cardErrorRes = null,
                            isCardAnalyzing = false
                        )
                    } else {
                        current.copy(
                            cardErrorRes = R.string.camera_scan_card_no_valid_number,
                            isCardAnalyzing = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != cardScanGeneration) return@launch
                _uiState.update {
                    it.copy(
                        cardErrorRes = R.string.camera_scan_card_recognition_failed,
                        isCardAnalyzing = false
                    )
                }
            } finally {
                cardAnalysisInFlight.set(false)
            }
        }
    }

    /** Clears the PAN and error so the analyzer resumes with a fresh frame. */
    fun rescanCard() {
        cardScanGeneration++
        _uiState.update {
            it.copy(
                cardNumber = null,
                cardErrorRes = null,
                isCardAnalyzing = false
            )
        }
    }

    fun setError(res: Int?) {
        _uiState.update { it.copy(errorRes = res) }
    }

    override fun onCleared() {
        cardScanGeneration++
        cardRecognizer.close()
        _uiState.update {
            it.copy(
                cardNumber = null,
                cardErrorRes = null,
                isCardAnalyzing = false,
                cardDownloadProgress = null
            )
        }
        super.onCleared()
    }

    class Factory(
        private val recognizerFactory: () -> CardNumberRecognizer = { MlKitCardNumberRecognizer() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraScanViewModel(recognizerFactory()) as T
        }
    }
}
