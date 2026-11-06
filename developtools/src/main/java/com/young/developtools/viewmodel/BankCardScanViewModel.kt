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

    /** Live camera path: first Luhn-valid card locks the result. */
    fun onTextRecognized(text: String) {
        if (_uiState.value.card != null) return
        val card = firstBankCard(text) ?: return
        _uiState.update { it.copy(card = card, cards = listOf(card), fullText = text, errorRes = null) }
    }

    /** Gallery path: recognize text from an image, then parse all cards. */
    fun recognizeImage(image: InputImage) {
        viewModelScope.launch {
            try {
                val visionText = recognizer.process(image).await()
                val text = visionText.text
                val cards = parseBankCards(text)
                _uiState.update {
                    it.copy(
                        card = cards.firstOrNull(),
                        cards = cards,
                        fullText = text,
                        errorRes = if (cards.isEmpty()) R.string.bank_card_scan_no_card else null,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.bank_card_scan_error) }
            }
        }
    }

    fun resetScan() {
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
