package com.young.developtools.viewmodel

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import kotlinx.coroutines.CancellationException

/** On-device model availability for the card-number prompt flow. */
enum class CardModelStatus {
    CHECKING,
    DOWNLOAD_REQUIRED,
    DOWNLOADING,
    AVAILABLE,
    /** The model cannot run here; card numbers are read from OCR text locally. */
    LOCAL_ONLY,
    UNAVAILABLE
}

/**
 * Abstraction over the on-device OCR-to-PAN pipeline so the ViewModel can be
 * tested without camera hardware, AICore, or a downloaded model.
 */
interface CardNumberRecognizer {

    suspend fun checkAvailability(): CardModelStatus

    /** Downloads the on-device model, reporting 0-100 progress. */
    suspend fun download(onProgress: (Int) -> Unit): Boolean

    /** Sends OCR text (never an image) to the model and returns its raw candidate. */
    suspend fun extractCandidate(ocrText: String): String?

    fun close()
}

/**
 * ML Kit GenAI Prompt API implementation.
 *
 * The model refines the OCR result; it never replaces local validation. Every
 * failure path degrades to a visible state and there is no cloud fallback.
 * The OCR text and the model response stay in memory and are never logged.
 */
class MlKitCardNumberRecognizer : CardNumberRecognizer {

    private val clientLock = Any()
    private var client: GenerativeModel? = null

    private fun clientOrNull(): GenerativeModel? = synchronized(clientLock) {
        client ?: runCatching { Generation.getClient() }.getOrNull()?.also { client = it }
    }

    override suspend fun checkAvailability(): CardModelStatus {
        val model = clientOrNull() ?: return CardModelStatus.LOCAL_ONLY
        return try {
            val status = model.checkStatus()
            // checkStatus() also populates the optional-feature map that the
            // structured-output probe reads, so the order matters.
            val structuredOutput = model.isStructuredOutputFeatureAvailable()
            when (status) {
                FeatureStatus.AVAILABLE -> {
                    if (structuredOutput) {
                        CardModelStatus.AVAILABLE
                    } else {
                        CardModelStatus.LOCAL_ONLY
                    }
                }
                FeatureStatus.DOWNLOADABLE,
                FeatureStatus.DOWNLOADING -> {
                    if (structuredOutput) {
                        CardModelStatus.DOWNLOAD_REQUIRED
                    } else {
                        CardModelStatus.LOCAL_ONLY
                    }
                }
                else -> CardModelStatus.LOCAL_ONLY
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Missing AICore, an unsupported device, or a transient 606 all
            // degrade to local OCR instead of blocking card scanning.
            CardModelStatus.LOCAL_ONLY
        }
    }

    override suspend fun download(onProgress: (Int) -> Unit): Boolean {
        val model = clientOrNull() ?: return false
        return try {
            var totalBytes = 0L
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> totalBytes = status.bytesToDownload
                    is DownloadStatus.DownloadProgress -> {
                        if (totalBytes > 0) {
                            val progress = status.totalBytesDownloaded * 100 / totalBytes
                            onProgress(progress.toInt().coerceIn(0, 100))
                        }
                    }
                    is DownloadStatus.DownloadCompleted -> onProgress(100)
                    is DownloadStatus.DownloadFailed -> throw status.e
                }
            }
            checkAvailability() == CardModelStatus.AVAILABLE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractCandidate(ocrText: String): String? {
        val model = clientOrNull() ?: return null
        val request = GenerateContentRequest.Builder(TextPart(buildPrompt(ocrText))).build()
        val typedRequest = generateTypedContentRequest(request, CardNumberExtraction::class)
        val response = model.generateContent(typedRequest)
        val extraction = response.candidates.firstOrNull()?.response ?: return null
        if (!extraction.found) return null
        return extraction.cardNumber.takeIf { it.isNotBlank() }
    }

    override fun close() {
        synchronized(clientLock) {
            client?.close()
            client = null
        }
    }

    private fun buildPrompt(ocrText: String): String = buildString {
        appendLine(
            "Extract only the payment card number from the OCR text below. " +
                "Return a 12-19 digit number exactly as written, spaces or hyphens allowed. " +
                "Never infer, complete, or invent digits. Do not return the cardholder name, " +
                "expiry date, CVV, issuer, or any other field. If no card number is present, " +
                "set found to false and cardNumber to an empty string."
        )
        appendLine()
        appendLine("OCR text:")
        append(ocrText.take(MAX_OCR_CHARS))
    }

    private companion object {
        const val MAX_OCR_CHARS = 4_000
    }
}
