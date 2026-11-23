package com.young.developtools.viewmodel

import android.graphics.Bitmap
import android.graphics.Rect
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
import com.young.developtools.data.cardNumberRuns
import com.young.developtools.data.longestDigitRun
import com.young.developtools.data.normalizeOcrDigits
import com.young.developtools.data.parseBankCards
import com.young.developtools.data.parseIdCardEmblem
import com.young.developtools.data.parseIdCardPortrait
import com.young.developtools.data.voteCardReadings
import com.young.developtools.utils.DevLog
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
import kotlin.math.max

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

    // Redacted stage summary of the last gallery scan. Never contains OCR text,
    // card digits, bitmaps, or hashes; cleared when a scan starts or resets.
    @Volatile
    private var lastScanTrace: List<BankCardScanTraceEntry> = emptyList()

    private val _diagnostics = MutableStateFlow<BankCardScanDiagnostics?>(null)
    internal val diagnostics: StateFlow<BankCardScanDiagnostics?> = _diagnostics.asStateFlow()

    /** Redacted per-pass diagnostics for manual validation; empty between scans. */
    internal fun scanTrace(): List<BankCardScanTraceEntry> = lastScanTrace

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
        val frameRotation = if (cardType == CardType.BANK_CARD) {
            preferredBankCardRotation ?: BANK_CARD_ROTATION_ANGLES[nextBankCardRotationIndex].also {
                nextBankCardRotationIndex = (nextBankCardRotationIndex + 1) % BANK_CARD_ROTATION_ANGLES.size
            }
        } else {
            0f
        }
        viewModelScope.launch {
            try {
                when (cardType) {
                    CardType.BANK_CARD -> recognizeLiveBankCard(bitmap, frameRotation)
                    CardType.ID_CARD -> {
                        val visionText = recognizerFor(cardType)
                            .process(InputImage.fromBitmap(bitmap, 0))
                            .await()
                        onTextRecognized(visionText.text, visionText.toOcrLines())
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.card_scan_error) }
            } finally {
                recognitionBusy.set(false)
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Live bank-card path. The frame is localized and rectified first, so the
     * number line no longer depends on the card sitting in the centred crop.
     * Raw OCR is always first; the heavier variants only run when it produced
     * no card. The arbitrary rotation sweep stays as the fallback for a frame
     * where the card outline could not be found.
     */
    private suspend fun recognizeLiveBankCard(frame: Bitmap, frameRotation: Float) {
        val region = withContext(Dispatchers.Default) {
            ImagePreprocessor.localizeCardForOcr(frame, LIVE_OCR_LONG_SIDE)
        }
        val card = region.bitmap
        var recognizedText = ""
        var successfulOrientation: Float? = null
        try {
            val orientations = if (region.rectified) {
                if (preferredBankCardRotation == 180f) listOf(180f, 0f) else listOf(0f, 180f)
            } else {
                listOf(frameRotation)
            }
            for (orientation in orientations) {
                val oriented = if (orientation == 0f) {
                    card
                } else {
                    withContext(Dispatchers.Default) {
                        ImagePreprocessor.rotateForOcr(card, orientation, LIVE_OCR_LONG_SIDE)
                    }
                }
                try {
                    recognizedText = recognizerFor(CardType.BANK_CARD)
                        .process(InputImage.fromBitmap(oriented, 0))
                        .await()
                        .text
                    if (parseBankCards(recognizedText).isEmpty()) {
                        val enhanced = withContext(Dispatchers.Default) {
                            ImagePreprocessor.enhanceForOcr(oriented, LIVE_OCR_LONG_SIDE)
                        }
                        try {
                            recognizedText = recognizerFor(CardType.BANK_CARD)
                                .process(InputImage.fromBitmap(enhanced, 0))
                                .await()
                                .text
                        } finally {
                            if (!enhanced.isRecycled) enhanced.recycle()
                        }
                    }
                    // Silver-embossed: min(R,G,B) separates silver/white
                    // digits from coloured backgrounds far better than
                    // standard grayscale. Try only the first (soft) variant
                    // in the live path to stay bounded.
                    if (parseBankCards(recognizedText).isEmpty()) {
                        val silverVariants = withContext(Dispatchers.Default) {
                            ImagePreprocessor.enhanceForOcrSilverEmbossed(oriented, LIVE_OCR_LONG_SIDE)
                        }
                        for (variant in silverVariants) {
                            try {
                                val silverText = recognizerFor(CardType.BANK_CARD)
                                    .process(InputImage.fromBitmap(variant, 0))
                                    .await()
                                    .text
                                if (parseBankCards(silverText).isNotEmpty()) {
                                    recognizedText = silverText
                                    break
                                }
                            } finally {
                                if (!variant.isRecycled) variant.recycle()
                            }
                        }
                    }
                    if (parseBankCards(recognizedText).isEmpty() &&
                        (region.rectified || orientation % 90f == 0f)
                    ) {
                        liveRowPass(oriented)?.let { recognizedText = it }
                    }
                } finally {
                    if (oriented !== card && !oriented.isRecycled) oriented.recycle()
                }
                if (parseBankCards(recognizedText).isNotEmpty()) {
                    successfulOrientation = orientation
                    break
                }
            }

            if (successfulOrientation != null) {
                preferredBankCardRotation = successfulOrientation
                missedPreferredRotationFrames = 0
            } else if (preferredBankCardRotation != null) {
                missedPreferredRotationFrames++
                if (missedPreferredRotationFrames >= PREFERRED_ROTATION_MISS_LIMIT) {
                    preferredBankCardRotation = null
                    missedPreferredRotationFrames = 0
                }
            }
            onTextRecognized(recognizedText)
        } finally {
            if (card !== frame && !card.isRecycled) card.recycle()
        }
    }

    /** Bounded live fallback: wide band, binary band, then the flattened number line. */
    private suspend fun liveRowPass(card: Bitmap): String? {
        val variants = listOf<() -> Bitmap?>(
            { ImagePreprocessor.prepareBankCardNumberBandForOcr(card, LIVE_OCR_LONG_SIDE) },
            { ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(card, LIVE_OCR_LONG_SIDE) },
            { ImagePreprocessor.prepareBankCardNumberLineRawForOcr(card, LIVE_OCR_LONG_SIDE) },
            { ImagePreprocessor.prepareBankCardNumberLineForOcr(card, LIVE_OCR_LONG_SIDE) },
            {
                ImagePreprocessor.prepareBankCardNumberLineAdaptiveForOcr(
                    card, LIVE_OCR_LONG_SIDE
                )
            },
            { ImagePreprocessor.prepareBankCardNumberLineStrongForOcr(card, LIVE_OCR_LONG_SIDE) },
        )
        for (create in variants) {
            val bitmap = withContext(Dispatchers.Default) { create() } ?: continue
            try {
                val text = recognizerFor(CardType.BANK_CARD)
                    .process(InputImage.fromBitmap(bitmap, 0))
                    .await()
                    .text
                if (parseBankCards(text).isNotEmpty()) return text
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        return null
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
        lastScanTrace = emptyList()
        _diagnostics.value = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (cardType) {
                    CardType.BANK_CARD -> {
                        if (ownedBitmap == null) {
                            val visionText = recognizerFor(cardType).process(image).await()
                            val accepted = BankCardRotationConfirmer()
                                .observe(visionText.text, 0f, BankCardOcrPass.FULL)
                            _uiState.update {
                                it.copy(
                                    bankCard = accepted.firstOrNull(),
                                    errorRes = if (accepted.isEmpty()) {
                                        R.string.card_scan_no_card
                                    } else {
                                        null
                                    },
                                )
                            }
                        } else {
                            recognizeBankCardFromGallery(ownedBitmap)
                        }
                    }
                    CardType.ID_CARD -> {
                        val visionText = recognizerFor(cardType).process(image).await()
                        val text = visionText.text
                        val parseLines = visionText.toOcrLines().ifEmpty { text.toOcrLines() }
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

    /**
     * Gallery bank-card path: localize and rectify the card, run the raw pass
     * first, and only then the bounded enhancement variants. A card that was
     * not localized keeps the rotation sweep as its fallback. All temporary
     * bitmaps are recycled as soon as their awaited ML Kit task completes.
     */
    private suspend fun recognizeBankCardFromGallery(source: Bitmap) {
        val confirmer = BankCardRotationConfirmer()
        val trace = mutableListOf<BankCardScanTraceEntry>()
        var cards: List<BankCard> = emptyList()
        val region = withContext(Dispatchers.Default) {
            ImagePreprocessor.localizeCardForOcr(source, MAX_OCR_LONG_SIDE)
        }
        val card = region.bitmap.let { original ->
            // Only downscale to the cap; upscaling a low-res photo just blurs
            // strokes and makes ML Kit worse. Number-line crops upscale their
            // narrow strip via scaleToCap, which is enough for small glyphs.
            ImagePreprocessor.enforceMaxLongSide(original, MAX_OCR_LONG_SIDE).also {
                if (it !== original) original.recycle()
            }
        }
        try {
            // A rectified card is already landscape; only its upright direction
            // is unknown, so 0 and 180 degrees cover it. An unrectified source
            // keeps the single pass and defers to the rotation sweep.
            val orientations = if (region.rectified) floatArrayOf(0f, 180f) else floatArrayOf(0f)
            for (orientation in orientations) {
                val oriented = if (orientation == 0f) {
                    card
                } else {
                    withContext(Dispatchers.Default) {
                        ImagePreprocessor.rotateForOcr(card, orientation, MAX_OCR_LONG_SIDE)
                    }
                }
                try {
                    val full = runBankPass(
                        bitmap = oriented,
                        recognizer = latinRecognizer,
                        pass = BankCardOcrPass.FULL,
                        rotation = orientation,
                        crop = null,
                        confirmer = confirmer,
                        trace = trace,
                    )
                    cards = full.accepted
                    if (cards.isEmpty()) {
                        val enhanced = withContext(Dispatchers.Default) {
                            ImagePreprocessor.enhanceForOcr(oriented, MAX_OCR_LONG_SIDE)
                        }
                        try {
                            cards = runBankPass(
                                bitmap = enhanced,
                                recognizer = latinRecognizer,
                                pass = BankCardOcrPass.ENHANCED_FULL,
                                rotation = orientation,
                                crop = null,
                                confirmer = confirmer,
                                trace = trace,
                            ).accepted
                        } finally {
                            if (!enhanced.isRecycled) enhanced.recycle()
                        }
                    }
                    // Silver-embossed: min(R,G,B) → CLAHE → soft/binary
                    // variants. Much better than standard grayscale for
                    // silver/white digits on coloured backgrounds.
                    if (cards.isEmpty()) {
                        val silverVariants = withContext(Dispatchers.Default) {
                            ImagePreprocessor.enhanceForOcrSilverEmbossed(oriented, MAX_OCR_LONG_SIDE)
                        }
                        for ((idx, variant) in silverVariants.withIndex()) {
                            try {
                                cards = runBankPass(
                                    bitmap = variant,
                                    recognizer = latinRecognizer,
                                    pass = if (idx == 0) {
                                        BankCardOcrPass.SILVER_SOFT_FULL
                                    } else {
                                        BankCardOcrPass.SILVER_BINARY_FULL
                                    },
                                    rotation = orientation,
                                    crop = null,
                                    confirmer = confirmer,
                                    trace = trace,
                                ).accepted
                                if (cards.isNotEmpty()) break
                            } finally {
                                if (!variant.isRecycled) variant.recycle()
                            }
                        }
                    }
                    if (cards.isEmpty()) {
                        val row = ImagePreprocessor.numberRowFromLines(
                            full.text.toOcrLines(),
                            oriented.width,
                            oriented.height,
                        )
                        cards = runBankRowPasses(oriented, row, orientation, confirmer, trace)
                    }
                    if (cards.isEmpty()) cards = confirmer.vote(orientation)
                } finally {
                    if (oriented !== card && !oriented.isRecycled) oriented.recycle()
                }
                if (cards.isNotEmpty()) break
            }

            // A rectified card that still produced nothing may have been warped
            // from an imprecise quad, so the full-image rotation route stays as
            // the last resort instead of failing the scan outright.
            if (cards.isEmpty()) {
                cards = runBankRotationFallback(source, confirmer, trace)
            }
            // Last resort: pool stitched readings across all rotations and
            // vote. A card that only produced 1-2 stitched runs per rotation
            // may have 4+ readings total when every rotation is counted.
            if (cards.isEmpty()) {
                cards = confirmer.voteAllRotations()
            }
            lastScanTrace = trace
            _diagnostics.value = BankCardScanDiagnostics(region.rectified, trace)
            // Redacted summary only: pass names, counts and run lengths. No OCR
            // text, card digits, pixels or hashes are ever logged.
            DevLog.i(
                "CardScanTrace",
                buildString {
                    append("rectified=").append(region.rectified)
                    append(" passes=").append(trace.size)
                    append(" text=").append(trace.count { it.blockCount > 0 })
                    append(" runs=").append(trace.count { it.digitRunLengths.isNotEmpty() })
                    append(" maxDigits=").append(trace.maxOfOrNull { it.longestDigitRun } ?: 0)
                    append(" candidates=").append(trace.sumOf { it.candidateCount })
                },
            )
            _uiState.update {
                it.copy(
                    bankCard = cards.firstOrNull(),
                    errorRes = if (cards.isEmpty()) R.string.card_scan_no_card else null,
                )
            }
        } finally {
            if (card !== source && !card.isRecycled) card.recycle()
        }
    }

    /**
     * Band, binary band and number-line variants on a card image. Rows are
     * card-normalized; an OCR-derived row is preferred when the full pass saw
     * the number, otherwise three nearby rows are tried.
     */
    private suspend fun runBankRowPasses(
        card: Bitmap,
        row: ImagePreprocessor.NumberRow?,
        rotation: Float,
        confirmer: BankCardRotationConfirmer,
        trace: MutableList<BankCardScanTraceEntry>,
    ): List<BankCard> {
        suspend fun runVariant(
            recognizer: TextRecognizer,
            pass: BankCardOcrPass,
            bounds: IntArray,
            create: () -> Bitmap,
        ): List<BankCard> {
            val bitmap = withContext(Dispatchers.Default) { create() }
            return try {
                runBankPass(bitmap, recognizer, pass, rotation, bounds, confirmer, trace).accepted
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        val bandRow = row?.band() ?: ImagePreprocessor.NUMBER_BAND_ROW
        val bandBounds = ImagePreprocessor.rowCropBounds(card.width, card.height, bandRow)
        var cards = runVariant(latinRecognizer, BankCardOcrPass.NUMBER_BAND, bandBounds) {
            ImagePreprocessor.prepareBankCardNumberBandForOcr(card, MAX_OCR_LONG_SIDE, bandRow)
        }
        if (cards.isNotEmpty()) return cards

        cards = runVariant(latinRecognizer, BankCardOcrPass.BINARY_NUMBER_BAND, bandBounds) {
            ImagePreprocessor.prepareBankCardNumberBandBinaryForOcr(card, MAX_OCR_LONG_SIDE, bandRow)
        }
        if (cards.isNotEmpty()) return cards

        // A derived row is only a hint. If it came from a fragment it can be
        // off by a third of the card, so the default rows are still tried.
        val rows = buildList {
            if (row != null) add(row)
            add(ImagePreprocessor.NUMBER_LINE_ROW)
            add(ImagePreprocessor.UPPER_NUMBER_LINE_ROW)
            add(ImagePreprocessor.LOWER_NUMBER_LINE_ROW)
            add(ImagePreprocessor.BOTTOM_NUMBER_LINE_ROW)
        }.distinct()
        for (lineRow in rows) {
            val rawPass = when (lineRow) {
                ImagePreprocessor.UPPER_NUMBER_LINE_ROW -> BankCardOcrPass.UPPER_NUMBER_LINE_RAW
                ImagePreprocessor.LOWER_NUMBER_LINE_ROW -> BankCardOcrPass.LOWER_NUMBER_LINE_RAW
                ImagePreprocessor.BOTTOM_NUMBER_LINE_ROW -> BankCardOcrPass.BOTTOM_NUMBER_LINE_RAW
                else -> BankCardOcrPass.NUMBER_LINE_RAW
            }
            val linePass = when (lineRow) {
                ImagePreprocessor.UPPER_NUMBER_LINE_ROW -> BankCardOcrPass.UPPER_NUMBER_LINE
                ImagePreprocessor.LOWER_NUMBER_LINE_ROW -> BankCardOcrPass.LOWER_NUMBER_LINE
                ImagePreprocessor.BOTTOM_NUMBER_LINE_ROW -> BankCardOcrPass.BOTTOM_NUMBER_LINE
                else -> BankCardOcrPass.NUMBER_LINE
            }
            val adaptivePass = when (lineRow) {
                ImagePreprocessor.UPPER_NUMBER_LINE_ROW ->
                    BankCardOcrPass.UPPER_ADAPTIVE_NUMBER_LINE
                ImagePreprocessor.LOWER_NUMBER_LINE_ROW ->
                    BankCardOcrPass.LOWER_ADAPTIVE_NUMBER_LINE
                ImagePreprocessor.BOTTOM_NUMBER_LINE_ROW ->
                    BankCardOcrPass.BOTTOM_ADAPTIVE_NUMBER_LINE
                else -> BankCardOcrPass.ADAPTIVE_NUMBER_LINE
            }
            val bounds = ImagePreprocessor.rowCropBounds(card.width, card.height, lineRow)

            // Unfiltered first: if the embossing already has contrast, filters
            // can only remove information the recognizer would have used.
            cards = runVariant(latinRecognizer, rawPass, bounds) {
                ImagePreprocessor.prepareBankCardNumberLineRawForOcr(
                    card, MAX_OCR_LONG_SIDE, lineRow
                )
            }
            if (cards.isNotEmpty()) return cards

            cards = runVariant(latinRecognizer, linePass, bounds) {
                ImagePreprocessor.prepareBankCardNumberLineForOcr(card, MAX_OCR_LONG_SIDE, lineRow)
            }
            if (cards.isNotEmpty()) return cards

            val adaptive = withContext(Dispatchers.Default) {
                ImagePreprocessor.prepareBankCardNumberLineAdaptiveForOcr(
                    card, MAX_OCR_LONG_SIDE, lineRow
                )
            }
            if (adaptive != null) {
                try {
                    cards = runBankPass(
                        bitmap = adaptive,
                        recognizer = latinRecognizer,
                        pass = adaptivePass,
                        rotation = rotation,
                        crop = bounds,
                        confirmer = confirmer,
                        trace = trace,
                    ).accepted
                } finally {
                    if (!adaptive.isRecycled) adaptive.recycle()
                }
                if (cards.isNotEmpty()) return cards
            }
        }

        // Very low-contrast embossing: one stronger relief on the primary row.
        val primaryRow = rows.first()
        val primaryBounds = ImagePreprocessor.rowCropBounds(card.width, card.height, primaryRow)
        cards = runVariant(latinRecognizer, BankCardOcrPass.STRONG_NUMBER_LINE, primaryBounds) {
            ImagePreprocessor.prepareBankCardNumberLineStrongForOcr(
                card, MAX_OCR_LONG_SIDE, primaryRow
            )
        }
        if (cards.isNotEmpty()) return cards

        // The bundled Chinese model segments low-contrast strokes differently
        // and also covers Latin digits, so it gets the last word on the primary line.
        return runVariant(
            chineseRecognizer,
            BankCardOcrPass.CHINESE_NUMBER_LINE,
            primaryBounds,
        ) {
            ImagePreprocessor.prepareBankCardNumberLineForOcr(card, MAX_OCR_LONG_SIDE, primaryRow)
        }
    }

    /**
     * Rotation fallback for a photo where the card outline was not found.
     * The raw pass runs at every angle; the heavier row variants only at the
     * cardinal angles, so the sweep stays bounded.
     */
    private suspend fun runBankRotationFallback(
        source: Bitmap,
        confirmer: BankCardRotationConfirmer,
        trace: MutableList<BankCardScanTraceEntry>,
    ): List<BankCard> {
        for (rotation in BANK_CARD_GALLERY_ROTATION_ANGLES) {
            val raw = withContext(Dispatchers.Default) {
                ImagePreprocessor.rotateForOcr(source, rotation, MAX_OCR_LONG_SIDE)
            }
            try {
                var cards = runBankPass(
                    bitmap = raw,
                    recognizer = latinRecognizer,
                    pass = BankCardOcrPass.FULL,
                    rotation = rotation,
                    crop = null,
                    confirmer = confirmer,
                    trace = trace,
                ).accepted
                if (cards.isEmpty() && rotation % 90f == 0f) {
                    // One row only: the full sweep already spends a raw pass on
                    // every angle, so the heavier variants stay bounded.
                    cards = runBankRowPasses(
                        raw,
                        ImagePreprocessor.NUMBER_LINE_ROW,
                        rotation,
                        confirmer,
                        trace,
                    )
                }
                if (cards.isEmpty()) cards = confirmer.vote(rotation)
                if (cards.isNotEmpty()) return cards
            } finally {
                if (raw !== source && !raw.isRecycled) raw.recycle()
            }
        }
        return emptyList()
    }

    /** One OCR pass plus its redacted trace entry. Never records OCR text. */
    private suspend fun runBankPass(
        bitmap: Bitmap,
        recognizer: TextRecognizer,
        pass: BankCardOcrPass,
        rotation: Float,
        crop: IntArray?,
        confirmer: BankCardRotationConfirmer,
        trace: MutableList<BankCardScanTraceEntry>,
        lineGuided: Boolean = false,
    ): BankPassOutcome {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        var accepted = confirmer.observe(text.text, rotation, pass)
        // Per-fragment digit counts: every consecutive-digit chunk in the
        // normalized text, sorted descending. Redacted: lengths only, never
        // the digits themselves.
        val normalized = normalizeOcrDigits(text.text)
        val fragments = Regex("""\d+""").findAll(normalized)
            .map { it.value.length }
            .sortedDescending()
            .toList()
        trace += BankCardScanTraceEntry(
            pass = pass,
            rotation = rotation,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            crop = crop?.toList(),
            blockCount = text.textBlocks.size,
            lineCount = text.textBlocks.sumOf { it.lines.size },
            digitRunLengths = cardNumberRuns(text.text).map(String::length),
            longestDigitRun = longestDigitRun(text.text),
            candidateCount = parseBankCards(text.text).size,
            acceptedCount = accepted.size,
            fragmentLengths = fragments,
        )
        // A card-length run that still failed validation means the recognizer
        // found the number line but dropped or misread part of it. Re-read that
        // strip alone, enlarged, rather than trying more blind crops.
        if (!lineGuided && accepted.isEmpty() && cardNumberRuns(text.text).isNotEmpty()) {
            val line = cardDigitLine(text)
            if (line != null) {
                accepted = runLineGuidedPass(bitmap, recognizer, rotation, line, confirmer, trace)
            }
        }
        return BankPassOutcome(text, accepted)
    }

    /**
     * Bounding box of the digit lines that together read a card number, or null
     * when they carry too few digits to be one.
     *
     * A long embossed number is regularly split across several OCR lines — a
     * tilted print line, a reflection gap, a hologram — so requiring a single
     * line to hold the whole run finds nothing. Digit-bearing lines are instead
     * clustered by vertical proximity and the cluster with the most digits wins;
     * the expiry date sits far enough below to stay in its own cluster.
     */
    private fun cardDigitLine(text: Text): Rect? {
        val candidates = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line -> line.boundingBox?.let { box -> line to box } }
            .filter { (line, _) -> line.text.count(Char::isDigit) >= MIN_GUIDED_LINE_DIGITS }
            .sortedBy { (_, box) -> (box.top + box.bottom) / 2f }
        if (candidates.isEmpty()) return null

        val clusters = mutableListOf<MutableList<Pair<Text.Line, Rect>>>()
        for (candidate in candidates) {
            val cluster = clusters.lastOrNull()
            val previous = cluster?.lastOrNull()
            val gapLimit = if (previous == null) {
                0f
            } else {
                max(previous.second.height(), candidate.second.height())
                    .coerceAtLeast(1) * GUIDED_CLUSTER_GAP
            }
            val centre = (candidate.second.top + candidate.second.bottom) / 2f
            val previousCentre = previous?.let { (it.second.top + it.second.bottom) / 2f }
            if (cluster != null && previousCentre != null && centre - previousCentre <= gapLimit) {
                cluster += candidate
            } else {
                clusters += mutableListOf(candidate)
            }
        }

        val best = clusters.maxByOrNull { cluster ->
            cluster.sumOf { (line, _) -> line.text.count(Char::isDigit) }
        } ?: return null
        if (best.sumOf { (line, _) -> line.text.count(Char::isDigit) } < MIN_GUIDED_CLUSTER_DIGITS) {
            return null
        }
        return Rect(
            best.minOf { (_, box) -> box.left },
            best.minOf { (_, box) -> box.top },
            best.maxOf { (_, box) -> box.right },
            best.maxOf { (_, box) -> box.bottom },
        )
    }

    /** Re-reads one located number line, raw first and then flattened. */
    private suspend fun runLineGuidedPass(
        source: Bitmap,
        recognizer: TextRecognizer,
        rotation: Float,
        line: Rect,
        confirmer: BankCardRotationConfirmer,
        trace: MutableList<BankCardScanTraceEntry>,
    ): List<BankCard> {
        var accepted: List<BankCard> = emptyList()
        for (flatten in booleanArrayOf(false, true)) {
            val crop = withContext(Dispatchers.Default) {
                ImagePreprocessor.cropLineForOcr(source, line, MAX_OCR_LONG_SIDE, flatten)
            }
            try {
                accepted = runBankPass(
                    bitmap = crop,
                    recognizer = recognizer,
                    pass = if (flatten) {
                        BankCardOcrPass.LINE_GUIDED_FLAT
                    } else {
                        BankCardOcrPass.LINE_GUIDED
                    },
                    rotation = rotation,
                    crop = intArrayOf(0, line.top, source.width, line.bottom),
                    confirmer = confirmer,
                    trace = trace,
                    lineGuided = true,
                ).accepted
            } finally {
                if (!crop.isRecycled) crop.recycle()
            }
            if (accepted.isNotEmpty()) break
        }
        return accepted
    }

    fun setImageRecognitionInProgress(inProgress: Boolean) {
        _uiState.update { it.copy(isImageRecognitionInProgress = inProgress) }
    }

    fun resetScan() {
        clearVotes()
        lastScanTrace = emptyList()
        _diagnostics.value = null
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
        // A line needs a few digits to count as a fragment of the number; the
        // cluster it joins must then carry enough of them to be worth cropping.
        const val MIN_GUIDED_LINE_DIGITS = 4
        const val MIN_GUIDED_CLUSTER_DIGITS = 8
        // Line centres further apart than this many line heights are separate
        // rows (the expiry date), not fragments of one number. A tilted print
        // line drifts by more than one line height across the card, so the gap
        // has to tolerate that or the fragments never merge.
        const val GUIDED_CLUSTER_GAP = 2.2f
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

/** Result of one OCR pass: the full ML Kit text plus any confirmed candidates. */
private data class BankPassOutcome(val text: Text, val accepted: List<BankCard>)

/** Redacted scan summary for the developer diagnostics panel. */
internal data class BankCardScanDiagnostics(
    val rectified: Boolean,
    val entries: List<BankCardScanTraceEntry>,
)

/**
 * Redacted diagnostics for one gallery pass. Deliberately excludes OCR text,
 * card digits, bitmap pixels, and stable hashes of either.
 */
internal data class BankCardScanTraceEntry(
    val pass: BankCardOcrPass,
    val rotation: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val crop: List<Int>?,
    val blockCount: Int,
    val lineCount: Int,
    val digitRunLengths: List<Int>,
    val longestDigitRun: Int,
    val candidateCount: Int,
    val acceptedCount: Int,
    /** Lengths of every consecutive-digit fragment (no minimum), sorted descending. */
    val fragmentLengths: List<Int> = emptyList(),
)

internal class BankCardRotationConfirmer {
    private val rotationsByCandidate = mutableMapOf<String, MutableSet<Float>>()
    private val passesByCandidateAndRotation = mutableMapOf<String, MutableMap<Float, MutableSet<BankCardOcrPass>>>()
    private val readingsByRotation = mutableMapOf<Float, MutableList<String>>()

    fun observe(text: String, rotation: Float, pass: BankCardOcrPass): List<BankCard> {
        readingsByRotation.getOrPut(rotation) { mutableListOf() } += cardNumberRuns(text)
        return parseBankCards(text).filter { candidate ->
            val rotations = rotationsByCandidate.getOrPut(candidate.number) { mutableSetOf() }
            rotations.add(rotation)
            val passes = passesByCandidateAndRotation
                .getOrPut(candidate.number) { mutableMapOf() }
                .getOrPut(rotation) { mutableSetOf() }
            passes.add(pass)
            rotations.size >= GALLERY_ROTATION_CONFIRM_COUNT || passes.size >= 2
        }
    }

    /**
     * Per-position majority over every reading taken at [rotation], then Luhn.
     * Recovers a number no single pass read cleanly, which is the usual outcome
     * for long embossed numbers.
     */
    fun vote(rotation: Float): List<BankCard> {
        val number = voteCardReadings(readingsByRotation[rotation].orEmpty()) ?: return emptyList()
        return parseBankCards(number)
    }

    /**
     * Cross-rotation vote: pools readings from ALL rotations and votes across
     * them. This collects more readings for cards where each rotation only
     * produces 1-2 stitched runs (too few for a per-rotation vote to reach
     * [MIN_VOTE_READINGS]).
     */
    fun voteAllRotations(): List<BankCard> {
        val allReadings = readingsByRotation.values.flatten()
        val number = voteCardReadings(allReadings) ?: return emptyList()
        return parseBankCards(number)
    }
}

internal enum class BankCardOcrPass {
    FULL,
    ENHANCED_FULL,
    NUMBER_BAND,
    BINARY_NUMBER_BAND,
    NUMBER_LINE_RAW,
    NUMBER_LINE,
    ADAPTIVE_NUMBER_LINE,
    UPPER_NUMBER_LINE_RAW,
    UPPER_NUMBER_LINE,
    UPPER_ADAPTIVE_NUMBER_LINE,
    LOWER_NUMBER_LINE_RAW,
    LOWER_NUMBER_LINE,
    LOWER_ADAPTIVE_NUMBER_LINE,
    BOTTOM_NUMBER_LINE_RAW,
    BOTTOM_NUMBER_LINE,
    BOTTOM_ADAPTIVE_NUMBER_LINE,
    STRONG_NUMBER_LINE,
    CHINESE_NUMBER_LINE,
    LINE_GUIDED,
    LINE_GUIDED_FLAT,
    SILVER_SOFT_FULL,
    SILVER_BINARY_FULL,
}
