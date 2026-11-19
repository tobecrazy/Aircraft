package com.young.developtools.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.young.developtools.R
import com.young.developtools.data.BankCard
import com.young.developtools.data.CardType
import com.young.developtools.data.IdCardEmblem
import com.young.developtools.data.IdCardPortrait
import com.young.developtools.data.OcrLine
import com.young.developtools.data.ScanHint
import com.young.developtools.data.birthDateFromIdNumber
import com.young.developtools.data.parseBankCards
import com.young.developtools.data.parseIdCardEmblem
import com.young.developtools.data.parseIdCardPortrait
import com.young.developtools.utils.ImagePreprocessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

// shortcut: five-degree sampling can miss a narrow OCR tolerance; reduce the step if field testing shows misses.
internal val BANK_CARD_ROTATION_ANGLES = (
    listOf(0f, 90f, 180f, 270f, 45f, 135f, 225f, 315f) +
        (0 until 360 step 5).map(Int::toFloat)
    ).distinct()

// Gallery angles are coarse because each angle can require multiple on-device OCR passes.
internal val BANK_CARD_GALLERY_ROTATION_ANGLES = (
    listOf(0f, 90f, 270f, 180f, 15f, 345f, 75f, 105f, 255f, 285f, 165f, 195f) +
        (0 until 360 step 15).map(Int::toFloat)
    ).distinct()

private const val GALLERY_ROTATION_CONFIRM_COUNT = 2

data class CardScanUiState(
    val cardType: CardType = CardType.BANK_CARD,
    val bankCard: BankCard? = null,
    val idPortrait: IdCardPortrait? = null,
    val idEmblem: IdCardEmblem? = null,
    val hint: ScanHint? = null,
    val errorRes: Int? = null,
    val lens: ScanLens = ScanLens.BACK,
    val isImageRecognitionInProgress: Boolean = false,
) {
    val hasResult: Boolean
        get() = bankCard != null || idPortrait != null || idEmblem != null
}

/**
 * Shared state machine for the bank card and ID card scanner.
 *
 * Live frames never lock on a single reading: ML Kit misreads a glyph often
 * enough that one frame is unreliable, so a candidate must repeat across
 * frames before it is accepted. Bank cards vote on the Luhn-valid number.
 *
 * The ID card mode does not ask the user which side they hold: a frame is
 * parsed as the portrait side first (it must contain a checksum-valid number)
 * and as the national-emblem side only when that fails, so the side is
 * detected from the content itself.
 *
 * The Latin recognizer handles bank cards; ID cards use the bundled Chinese
 * model, which also covers the Latin digits on the number.
 */
class CardScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CardScanUiState())
    val uiState: StateFlow<CardScanUiState> = _uiState.asStateFlow()

    // Lazily created so the state machine stays constructible in plain JVM tests,
    // where MlKitContext is never initialized.
    private val latinRecognizerDelegate = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val chineseRecognizerDelegate = lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latinRecognizer by latinRecognizerDelegate
    private val chineseRecognizer by chineseRecognizerDelegate

    // Cross-frame confirmation buffers. Only validated frames enter a vote.
    private val recentFrameTexts = ArrayDeque<String>()
    private val bankCandidateCounts = mutableMapOf<String, Int>()
    private val idPortraitFrames = ArrayDeque<IdCardPortrait>()
    private val idEmblemFrames = ArrayDeque<IdCardEmblem>()

    /** One recognition at a time; frames arriving meanwhile are dropped. */
    private val recognitionBusy = AtomicBoolean(false)
    @Volatile
    private var nextBankCardRotationIndex = 0
    @Volatile
    private var preferredBankCardRotation: Float? = null
    @Volatile
    private var missedPreferredRotationFrames = 0

    fun selectCardType(cardType: CardType) {
        if (_uiState.value.cardType == cardType) return
        clearVotes()
        _uiState.update { CardScanUiState(cardType = cardType, lens = it.lens) }
    }

    /** Live camera path: dispatches the recognized text to the active parser. */
    fun onTextRecognized(text: String, lines: List<OcrLine> = emptyList()) {
        if (_uiState.value.hasResult) return
        if (text.isBlank() && lines.isEmpty()) return
        when (_uiState.value.cardType) {
            CardType.BANK_CARD -> onBankCardText(text)
            CardType.ID_CARD -> onIdCardLines(lines.ifEmpty { text.toOcrLines() })
        }
    }

    /**
     * Live path: a bank candidate locks on its [STABLE_CONFIRM_COUNT]-th
     * occurrence within the sliding window. Real cards repeat across frames
     * while per-frame digit noise does not.
     */
    private fun onBankCardText(text: String) {
        if (text.isNotBlank()) {
            recentFrameTexts.addLast(text)
            while (recentFrameTexts.size > RECENT_FRAME_LIMIT) {
                val expired = recentFrameTexts.removeFirst()
                for (candidate in parseBankCards(expired)) {
                    val remaining = (bankCandidateCounts[candidate.number] ?: 1) - 1
                    if (remaining > 0) bankCandidateCounts[candidate.number] = remaining
                    else bankCandidateCounts.remove(candidate.number)
                }
            }
        }
        for (candidate in parseBankCards(text)) {
            val count = (bankCandidateCounts[candidate.number] ?: 0) + 1
            bankCandidateCounts[candidate.number] = count
            if (count >= STABLE_CONFIRM_COUNT) {
                clearVotes()
                _uiState.update { it.copy(bankCard = candidate, errorRes = null) }
                return
            }
        }
    }

    /**
     * Side detection lives here rather than in the UI: the portrait side is the
     * only one that can produce a checksum-valid number, so trying it first is
     * enough to tell the two sides apart.
     */
    private fun onIdCardLines(lines: List<OcrLine>) {
        val portrait = parseIdCardPortrait(lines)
        if (portrait != null) {
            idPortraitFrames.addLast(portrait)
            while (idPortraitFrames.size > FRAME_WINDOW) idPortraitFrames.removeFirst()
            val sameNumber = idPortraitFrames.filter { it.idNumber == portrait.idNumber }
            if (sameNumber.size < STABLE_CONFIRM_COUNT) return
            val merged = mergePortrait(sameNumber, portrait.idNumber)
            clearVotes()
            _uiState.update { it.copy(idPortrait = merged, errorRes = null) }
            return
        }

        val emblem = parseIdCardEmblem(lines) ?: return
        idEmblemFrames.addLast(emblem)
        while (idEmblemFrames.size > FRAME_WINDOW) idEmblemFrames.removeFirst()
        val sameValidity = idEmblemFrames.count {
            it.validFrom == emblem.validFrom && it.validTo == emblem.validTo
        }
        if (sameValidity < STABLE_CONFIRM_COUNT) return
        val merged = mergeEmblem(idEmblemFrames.toList())
        clearVotes()
        _uiState.update { it.copy(idEmblem = merged, errorRes = null) }
    }

    /**
     * Majority vote per field; ties go to the longer reading, which favours the
     * complete address/name over a truncated frame.
     */
    private fun mergePortrait(frames: List<IdCardPortrait>, idNumber: String): IdCardPortrait {
        fun pick(selector: (IdCardPortrait) -> String): String =
            frames.map(selector)
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .maxWithOrNull(compareBy({ it.value }, { it.key.length }))
                ?.key
                .orEmpty()

        return IdCardPortrait(
            name = pick { it.name },
            gender = pick { it.gender },
            ethnicity = pick { it.ethnicity },
            birth = birthDateFromIdNumber(idNumber).orEmpty(),
            address = pick { it.address },
            idNumber = idNumber,
        )
    }

    private fun mergeEmblem(frames: List<IdCardEmblem>): IdCardEmblem {
        val issuer = frames.map { it.issuer }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        val validity = frames.filter { it.validFrom.isNotBlank() }
            .groupingBy { it.validFrom to it.validTo }
            .eachCount()
            .entries
            .maxByOrNull { it.value }
            ?.key
        return IdCardEmblem(
            issuer = issuer,
            validFrom = validity?.first.orEmpty(),
            validTo = validity?.second.orEmpty(),
        )
    }

    /**
     * Live frame handoff. Takes ownership of [bitmap]: it is recycled once
     * recognition completes, or immediately when the frame is dropped.
     */
    fun recognizeFrame(bitmap: Bitmap) {
        if (_uiState.value.hasResult) {
            bitmap.recycle()
            return
        }
        if (!recognitionBusy.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }
        val cardType = _uiState.value.cardType
        val rotation = if (cardType == CardType.BANK_CARD) {
            preferredBankCardRotation ?: BANK_CARD_ROTATION_ANGLES[nextBankCardRotationIndex].also {
                nextBankCardRotationIndex = (nextBankCardRotationIndex + 1) % BANK_CARD_ROTATION_ANGLES.size
            }
        } else {
            0f
        }
        viewModelScope.launch {
            var preparedBitmap: Bitmap? = null
            var rotatedBitmap: Bitmap? = null
            var rawRotatedBitmap: Bitmap? = null
            var numberBandBitmap: Bitmap? = null
            var binaryNumberBandBitmap: Bitmap? = null
            var enhancementRequired = false
            try {
                val recognitionBitmap = if (cardType == CardType.BANK_CARD) {
                    withContext(Dispatchers.Default) {
                        enhancementRequired = ImagePreprocessor.needsBankCardEnhancement(bitmap)
                        val prepared = ImagePreprocessor.prepareBankCardForOcr(bitmap, LIVE_OCR_LONG_SIDE)
                        preparedBitmap = prepared
                        ImagePreprocessor.rotateForOcr(prepared, rotation, LIVE_OCR_LONG_SIDE)
                            .also { rotatedBitmap = it }
                    }
                } else {
                    bitmap
                }
                val text = recognizerFor(cardType)
                    .process(InputImage.fromBitmap(recognitionBitmap, 0))
                    .await()
                var confirmedBankText = if (cardType == CardType.BANK_CARD && enhancementRequired) {
                    val rawBitmap = withContext(Dispatchers.Default) {
                        ImagePreprocessor.rotateForOcr(bitmap, rotation, LIVE_OCR_LONG_SIDE)
                            .also { rawRotatedBitmap = it }
                    }
                    val rawText = recognizerFor(cardType)
                        .process(InputImage.fromBitmap(rawBitmap, 0))
                        .await()
                        .text
                    matchingBankCardText(rawText, text.text)
                } else {
                    text.text
                }
                if (cardType == CardType.BANK_CARD && enhancementRequired && rotation % 90f == 0f &&
                    parseBankCards(confirmedBankText).isEmpty()
                ) {
                    val rawBitmap = rawRotatedBitmap ?: withContext(Dispatchers.Default) {
                        ImagePreprocessor.rotateForOcr(bitmap, rotation, LIVE_OCR_LONG_SIDE)
                            .also { rawRotatedBitmap = it }
                    }
                    val numberBand = withContext(Dispatchers.Default) {
                        ImagePreprocessor.prepareBankCardNumberBandForOcr(
                            rawBitmap, LIVE_OCR_LONG_SIDE
                        ).also { numberBandBitmap = it }
                    }
                    val bandText = recognizerFor(cardType)
                        .process(InputImage.fromBitmap(numberBand, 0)).await().text
                    if (parseBankCards(bandText).isNotEmpty()) confirmedBankText = bandText
                    else {
                        val binaryBand = withContext(Dispatchers.Default) {
                            ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(
                                rawBitmap, LIVE_OCR_LONG_SIDE
                            ).also { binaryNumberBandBitmap = it }
                        }
                        val binaryText = recognizerFor(cardType)
                            .process(InputImage.fromBitmap(binaryBand, 0)).await().text
                        if (parseBankCards(binaryText).isNotEmpty()) confirmedBankText = binaryText
                    }
                }
                if (cardType == CardType.BANK_CARD) {
                    if (parseBankCards(confirmedBankText).isNotEmpty()) {
                        preferredBankCardRotation = rotation
                        missedPreferredRotationFrames = 0
                    } else if (preferredBankCardRotation != null) {
                        missedPreferredRotationFrames++
                        if (missedPreferredRotationFrames >= PREFERRED_ROTATION_MISS_LIMIT) {
                            preferredBankCardRotation = null
                            missedPreferredRotationFrames = 0
                        }
                    }
                }
                onTextRecognized(
                    confirmedBankText,
                    if (confirmedBankText == text.text) text.toOcrLines() else emptyList(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.card_scan_error) }
            } finally {
                recognitionBusy.set(false)
                rotatedBitmap?.takeIf {
                    it !== bitmap && it !== preparedBitmap && !it.isRecycled
                }?.recycle()
                rawRotatedBitmap?.takeIf {
                    it !== bitmap && it !== preparedBitmap && it !== rotatedBitmap && !it.isRecycled
                }?.recycle()
                numberBandBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
                binaryNumberBandBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
                preparedBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Gallery path: recognize text from an image, then parse it for the active
     * card type. Takes ownership of [ownedBitmap] when non-null — it is recycled
     * in the `finally` AFTER recognition completes, because `InputImage` holds a
     * reference (no copy) and recycling early makes ML Kit fail to lock pixels.
     */
    fun recognizeImage(image: InputImage, ownedBitmap: Bitmap? = null) {
        val cardType = _uiState.value.cardType
        _uiState.update { it.copy(isImageRecognitionInProgress = true, errorRes = null) }
        val rotationConfirmer = BankCardRotationConfirmer()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val visionText = recognizerFor(cardType).process(image).await()
                val text = visionText.text
                val lines = visionText.toOcrLines()
                when (cardType) {
                    CardType.BANK_CARD -> {
                        val enhance = ownedBitmap != null && withContext(Dispatchers.Default) {
                            ImagePreprocessor.needsBankCardEnhancement(ownedBitmap)
                        }
                        var enhancedBitmap: Bitmap? = null
                        try {
                            var finalCards = if (enhance) {
                                val enhanced = withContext(Dispatchers.Default) {
                                    ImagePreprocessor.prepareBankCardForOcr(
                                        checkNotNull(ownedBitmap), MAX_OCR_LONG_SIDE
                                    ).also { enhancedBitmap = it }
                                }
                                val enhancedText = recognizerFor(cardType)
                                    .process(InputImage.fromBitmap(enhanced, 0)).await().text
                                (rotationConfirmer.observe(text, 0f, BankCardOcrPass.FULL) +
                                    rotationConfirmer.observe(enhancedText, 0f, BankCardOcrPass.FULL))
                                    .distinctBy { it.number }
                            } else {
                                rotationConfirmer.observe(text, 0f, BankCardOcrPass.FULL)
                            }
                            if (finalCards.isEmpty() && ownedBitmap != null) {
                                for (rotation in BANK_CARD_GALLERY_ROTATION_ANGLES) {
                                    var rawRotated: Bitmap? = null
                                    var enhancedRotated: Bitmap? = null
                                    var numberBand: Bitmap? = null
                                    var binaryNumberBand: Bitmap? = null
                                    try {
                                        val raw = withContext(Dispatchers.Default) {
                                            ImagePreprocessor.rotateForOcr(
                                                ownedBitmap, rotation, MAX_OCR_LONG_SIDE
                                            ).also { rawRotated = it }
                                        }
                                        val rawText = recognizerFor(cardType)
                                            .process(InputImage.fromBitmap(raw, 0)).await().text
                                        val enhancedSource = enhancedBitmap
                                        val enhancedText = if (enhance && enhancedSource != null) {
                                            val enhanced = withContext(Dispatchers.Default) {
                                                ImagePreprocessor.rotateForOcr(
                                                    enhancedSource, rotation, MAX_OCR_LONG_SIDE
                                                ).also { enhancedRotated = it }
                                            }
                                            recognizerFor(cardType)
                                                .process(InputImage.fromBitmap(enhanced, 0)).await().text
                                        } else rawText
                                        finalCards = (rotationConfirmer.observe(
                                            rawText, rotation, BankCardOcrPass.FULL
                                        ) +
                                            if (enhancedText != rawText) {
                                                rotationConfirmer.observe(
                                                    enhancedText, rotation, BankCardOcrPass.FULL
                                                )
                                            } else {
                                                emptyList()
                                            }).distinctBy { it.number }
                                        if (finalCards.isEmpty() && rotation % 90f == 0f) {
                                            val band = withContext(Dispatchers.Default) {
                                                ImagePreprocessor.prepareBankCardNumberBandForOcr(
                                                    raw, MAX_OCR_LONG_SIDE
                                                ).also { numberBand = it }
                                            }
                                            finalCards = rotationConfirmer.observe(
                                                recognizerFor(cardType)
                                                    .process(InputImage.fromBitmap(band, 0)).await().text,
                                                rotation,
                                                BankCardOcrPass.NUMBER_BAND,
                                            )
                                            if (finalCards.isEmpty()) {
                                                val binaryBand = withContext(Dispatchers.Default) {
                                                    ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(
                                                        raw, MAX_OCR_LONG_SIDE
                                                    ).also { binaryNumberBand = it }
                                                }
                                                finalCards = rotationConfirmer.observe(
                                                    recognizerFor(cardType)
                                                        .process(InputImage.fromBitmap(binaryBand, 0)).await().text,
                                                    rotation,
                                                    BankCardOcrPass.BINARY_NUMBER_BAND,
                                                )
                                            }
                                        }
                                    } finally {
                                        rawRotated?.takeIf { it !== ownedBitmap && !it.isRecycled }?.recycle()
                                        enhancedRotated?.takeIf {
                                            it !== enhancedBitmap && !it.isRecycled
                                        }?.recycle()
                                        numberBand?.takeIf { !it.isRecycled }?.recycle()
                                        binaryNumberBand?.takeIf { !it.isRecycled }?.recycle()
                                    }
                                    if (finalCards.isNotEmpty()) break
                                }
                            }
                            _uiState.update {
                                it.copy(
                                    bankCard = finalCards.firstOrNull(),
                                    errorRes = if (finalCards.isEmpty()) R.string.card_scan_no_card else null,
                                )
                            }
                        } finally {
                            enhancedBitmap?.takeIf { it !== ownedBitmap && !it.isRecycled }?.recycle()
                        }
                    }
                    CardType.ID_CARD -> {
                        val parseLines = lines.ifEmpty { text.toOcrLines() }
                        val portrait = parseIdCardPortrait(parseLines)
                        val emblem = if (portrait == null) parseIdCardEmblem(parseLines) else null
                        _uiState.update {
                            it.copy(
                                idPortrait = portrait,
                                idEmblem = emblem,
                                errorRes = if (portrait == null && emblem == null) {
                                    R.string.card_scan_no_id
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.card_scan_error) }
            } finally {
                if (ownedBitmap?.isRecycled == false) ownedBitmap.recycle()
                _uiState.update { it.copy(isImageRecognitionInProgress = false) }
            }
        }
    }

    fun setImageRecognitionInProgress(inProgress: Boolean) {
        _uiState.update { it.copy(isImageRecognitionInProgress = inProgress) }
    }

    fun resetScan() {
        clearVotes()
        _uiState.update {
            it.copy(
                bankCard = null,
                idPortrait = null,
                idEmblem = null,
                hint = null,
                errorRes = null,
            )
        }
    }

    private fun clearVotes() {
        recentFrameTexts.clear()
        bankCandidateCounts.clear()
        idPortraitFrames.clear()
        idEmblemFrames.clear()
        preferredBankCardRotation = null
        missedPreferredRotationFrames = 0
        nextBankCardRotationIndex = 0
    }

    /** Advisory only: a hint never blocks recognition. */
    fun setHint(hint: ScanHint?) {
        if (_uiState.value.hint == hint) return
        _uiState.update { it.copy(hint = hint) }
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

    private fun recognizerFor(cardType: CardType): TextRecognizer =
        if (cardType == CardType.BANK_CARD) latinRecognizer else chineseRecognizer

    override fun onCleared() {
        if (latinRecognizerDelegate.isInitialized()) latinRecognizer.close()
        if (chineseRecognizerDelegate.isInitialized()) chineseRecognizer.close()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CardScanViewModel() as T
        }
    }

    private companion object {
        const val RECENT_FRAME_LIMIT = 30
        const val FRAME_WINDOW = 12
        const val STABLE_CONFIRM_COUNT = 3
        const val PREFERRED_ROTATION_MISS_LIMIT = 5
        const val LIVE_OCR_LONG_SIDE = 1600
        const val MAX_OCR_LONG_SIDE = 2048
    }
}

/** Maps ML Kit blocks to the position-aware lines the ID parser needs. */
private fun Text.toOcrLines(): List<OcrLine> = textBlocks.flatMap { block ->
    block.lines.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        OcrLine(line.text, box.left, box.top, box.right, box.bottom)
    }
}

/** Plain-text fallback for callers that only have the concatenated OCR text. */
private fun String.toOcrLines(): List<OcrLine> =
    split('\n').filter { it.isNotBlank() }.mapIndexed { index, text ->
        OcrLine(text = text, top = index, bottom = index + 1)
    }

internal fun matchingBankCardText(rawText: String, enhancedText: String): String {
    val rawNumbers = parseBankCards(rawText).mapTo(mutableSetOf()) { it.number }
    val enhancedNumbers = parseBankCards(enhancedText).mapTo(mutableSetOf()) { it.number }
    return when {
        rawNumbers.isEmpty() -> enhancedNumbers.firstOrNull().orEmpty()
        enhancedNumbers.isEmpty() -> rawNumbers.firstOrNull().orEmpty()
        else -> rawNumbers.firstOrNull { it in enhancedNumbers }.orEmpty()
    }
}

internal class BankCardRotationConfirmer {
    private val rotationsByCandidate = mutableMapOf<String, MutableSet<Float>>()
    private val passesByCandidateAndRotation = mutableMapOf<String, MutableMap<Float, MutableSet<BankCardOcrPass>>>()

    fun observe(text: String, rotation: Float, pass: BankCardOcrPass): List<BankCard> =
        parseBankCards(text).filter { candidate ->
            val rotations = rotationsByCandidate.getOrPut(candidate.number) { mutableSetOf() }
            rotations.add(rotation)
            val passes = passesByCandidateAndRotation
                .getOrPut(candidate.number) { mutableMapOf() }
                .getOrPut(rotation) { mutableSetOf() }
            passes.add(pass)
            rotations.size >= GALLERY_ROTATION_CONFIRM_COUNT || passes.size >= 2
        }
}

internal enum class BankCardOcrPass { FULL, NUMBER_BAND, BINARY_NUMBER_BAND }
