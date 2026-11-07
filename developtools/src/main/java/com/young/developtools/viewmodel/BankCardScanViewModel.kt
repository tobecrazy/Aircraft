package com.young.developtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.young.developtools.R
import com.young.developtools.data.BankCard
import com.young.developtools.data.firstBankCard
import com.young.developtools.data.parseBankCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.common.InputImage

data class BankCardScanUiState(
    val card: BankCard? = null,
    val cards: List<BankCard> = emptyList(),
    val fullText: String = "",
    val errorRes: Int? = null,
    val lens: ScanLens = ScanLens.BACK,
)

class BankCardScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BankCardScanUiState())
    val uiState: StateFlow<BankCardScanUiState> = _uiState.asStateFlow()

    // Lazily created so the state machine stays constructible in plain JVM tests,
    // where MlKitContext is never initialized.
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Cross-frame confirmation buffer: ML Kit misreads a digit in any single frame
    // often enough that the raw per-frame parse locks the wrong (or no) card.
    // Accumulate recent frames and require a candidate to appear repeatedly.
    private val recentFrameTexts = ArrayDeque<String>()
    private val candidateCounts = mutableMapOf<String, Int>()

    /**
     * Live camera path: a candidate locks on its STABLE_CONFIRM_COUNT-th
     * occurrence within the sliding window. Real cards repeat across frames
     * while per-frame digit noise does not, so counting is more robust than
     * requiring identical full-frame text.
     */
    fun onTextRecognized(text: String) {
        if (_uiState.value.card != null) return
        if (text.isNotBlank()) {
            recentFrameTexts.addLast(text)
            while (recentFrameTexts.size > RECENT_FRAME_LIMIT) {
                val expired = recentFrameTexts.removeFirst()
                for (candidate in parseBankCards(expired)) {
                    val remaining = (candidateCounts[candidate.number] ?: 1) - 1
                    if (remaining > 0) candidateCounts[candidate.number] = remaining
                    else candidateCounts.remove(candidate.number)
                }
            }
        }
        for (candidate in parseBankCards(text)) {
            val count = (candidateCounts[candidate.number] ?: 0) + 1
            candidateCounts[candidate.number] = count
            if (count >= STABLE_CONFIRM_COUNT) {
                recentFrameTexts.clear()
                candidateCounts.clear()
                _uiState.update {
                    it.copy(card = candidate, cards = listOf(candidate), fullText = text, errorRes = null)
                }
                return
            }
        }
        // Keep the latest OCR text visible so the user can see what the camera is reading,
        // even while it is still below the confirmation threshold.
        _uiState.update { it.copy(fullText = text) }
    }

    private companion object {
        const val RECENT_FRAME_LIMIT = 30
        const val STABLE_CONFIRM_COUNT = 3
    }

    /** Gallery path: recognize text from an image, then parse all cards. */
    fun recognizeImage(image: InputImage) {
        viewModelScope.launch {
            try {
                val visionText = recognizer.process(image).await()
                val text = visionText.text
                val cards = parseBankCards(text)
                // OCR over a single still can drop a digit; a second pass over the
                // same bitmap catches most of those single-frame failures cheaply.
                val finalCards = if (cards.isNotEmpty()) cards else parseBankCards(recognizer.process(image).await().text)
                _uiState.update {
                    it.copy(
                        card = finalCards.firstOrNull(),
                        cards = finalCards,
                        fullText = text,
                        errorRes = if (finalCards.isEmpty()) R.string.bank_card_scan_no_card else null,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.bank_card_scan_error) }
            }
        }
    }

    /**
     * Recognition helper exposed for the rotated-card path: rotate via the
     * caller-provided preprocessor, then parse the OCR text. Returns the
     * first Luhn-valid card, or null.
     */
    fun parseOcrText(text: String) = parseBankCards(text)

    fun resetScan() {
        recentFrameTexts.clear()
        candidateCounts.clear()
        _uiState.update { it.copy(card = null, cards = emptyList(), fullText = "", errorRes = null) }
    }

    fun setError(res: Int?) {
        _uiState.update { it.copy(errorRes = res) }
    }

    fun toggleLens() {
        _uiState.update {
            it.copy(lens = if (it.lens == ScanLens.FRONT) ScanLens.BACK else ScanLens.FRONT)
        }
    }

    fun useRearLens() {
        _uiState.update { it.copy(lens = ScanLens.BACK) }
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BankCardScanViewModel() as T
        }
    }
}
