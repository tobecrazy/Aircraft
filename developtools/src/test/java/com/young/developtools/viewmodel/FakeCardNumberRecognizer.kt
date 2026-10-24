package com.young.developtools.viewmodel

import kotlinx.coroutines.CompletableDeferred

/** Test double for [CardNumberRecognizer]; no AICore, model, or camera involved. */
internal class FakeCardNumberRecognizer(
    var status: CardModelStatus = CardModelStatus.AVAILABLE,
    var candidate: String? = null,
    var failure: Exception? = null,
    var downloadResult: Boolean = true,
    var gate: CompletableDeferred<Unit>? = null
) : CardNumberRecognizer {

    var availabilityCalls = 0
    var extractCalls = 0
    var downloadCalls = 0
    var closed = false
    var lastOcrText: String? = null

    override suspend fun checkAvailability(): CardModelStatus {
        availabilityCalls++
        return status
    }

    override suspend fun download(onProgress: (Int) -> Unit): Boolean {
        downloadCalls++
        onProgress(50)
        onProgress(100)
        if (downloadResult) status = CardModelStatus.AVAILABLE
        return downloadResult
    }

    override suspend fun extractCandidate(ocrText: String): String? {
        extractCalls++
        lastOcrText = ocrText
        gate?.await()
        failure?.let { throw it }
        return candidate
    }

    override fun close() {
        closed = true
    }
}
