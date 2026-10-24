package com.young.developtools.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.young.developtools.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraScanViewModelTest {

    private val visa = "4111111111111111"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(recognizer: FakeCardNumberRecognizer) = CameraScanViewModel(recognizer)

    // ── Initial state ────────────────────────────────────────

    @Test
    fun `starts in QR mode with the card model unchecked`() {
        val vm = viewModel(FakeCardNumberRecognizer())

        assertEquals(CameraScanMode.QR, vm.uiState.value.mode)
        assertEquals(CardModelStatus.CHECKING, vm.uiState.value.cardModelStatus)
        assertNull(vm.uiState.value.cardNumber)
    }

    @Test
    fun `QR scanning keeps first result and ignores later codes`() {
        val vm = viewModel(FakeCardNumberRecognizer())

        vm.onBarcodeDetected("first")
        vm.onBarcodeDetected("second")

        assertEquals("first", vm.uiState.value.result)
    }

    // ── Lens switching ───────────────────────────────────────

    @Test
    fun `camera switch toggles between rear and front`() {
        val vm = viewModel(FakeCardNumberRecognizer())

        assertEquals(CameraLens.BACK, vm.uiState.value.lensFacing)

        vm.toggleLens()
        assertEquals(CameraLens.FRONT, vm.uiState.value.lensFacing)

        vm.toggleLens()
        assertEquals(CameraLens.BACK, vm.uiState.value.lensFacing)
    }

    // ── Model availability ───────────────────────────────────

    @Test
    fun `entering card mode checks availability`() = runTest {
        val fake = FakeCardNumberRecognizer(status = CardModelStatus.AVAILABLE)
        val vm = viewModel(fake)

        vm.setMode(CameraScanMode.CARD)

        assertEquals(1, fake.availabilityCalls)
        assertEquals(CardModelStatus.AVAILABLE, vm.uiState.value.cardModelStatus)
    }

    @Test
    fun `unavailable model blocks inference`() = runTest {
        val fake = FakeCardNumberRecognizer(
            status = CardModelStatus.UNAVAILABLE,
            candidate = visa
        )
        val vm = viewModel(fake)

        vm.setMode(CameraScanMode.CARD)
        vm.onCardFrameText("CARD $visa")

        assertEquals(CardModelStatus.UNAVAILABLE, vm.uiState.value.cardModelStatus)
        assertEquals(0, fake.extractCalls)
        assertNull(vm.uiState.value.cardNumber)
    }

    @Test
    fun `download required exposes download state then becomes available`() = runTest {
        val fake = FakeCardNumberRecognizer(status = CardModelStatus.DOWNLOAD_REQUIRED)
        val vm = viewModel(fake)

        vm.setMode(CameraScanMode.CARD)
        assertEquals(CardModelStatus.DOWNLOAD_REQUIRED, vm.uiState.value.cardModelStatus)

        vm.downloadCardModel()

        assertEquals(1, fake.downloadCalls)
        assertEquals(CardModelStatus.AVAILABLE, vm.uiState.value.cardModelStatus)
        assertNull(vm.uiState.value.cardDownloadProgress)
    }

    @Test
    fun `download failure returns to download required with a message`() = runTest {
        val fake = FakeCardNumberRecognizer(
            status = CardModelStatus.DOWNLOAD_REQUIRED,
            downloadResult = false
        )
        val vm = viewModel(fake)

        vm.downloadCardModel()

        assertEquals(CardModelStatus.DOWNLOAD_REQUIRED, vm.uiState.value.cardModelStatus)
        assertEquals(R.string.camera_scan_card_download_failed, vm.uiState.value.cardModelErrorRes)
    }

    // ── Valid results ────────────────────────────────────────

    @Test
    fun `valid candidate is normalized and stored in memory`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = "4111 1111 1111 1111")
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD 4111-1111 1111 1111 EXP 12/30")

        assertEquals(visa, vm.uiState.value.cardNumber)
        assertNull(vm.uiState.value.cardErrorRes)
        assertFalse(vm.uiState.value.isCardAnalyzing)
    }

    @Test
    fun `result locks the scanner and ignores later frames`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = visa)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD $visa")
        vm.onCardFrameText("CARD 5500005555555559")

        assertEquals(visa, vm.uiState.value.cardNumber)
        assertEquals(1, fake.extractCalls)
    }

    // ── Rejected results ─────────────────────────────────────

    @Test
    fun `invalid luhn reports no valid number and requires a rescan`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = "4111111111111112")
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD 4111 1111 1111 1112")

        assertNull(vm.uiState.value.cardNumber)
        assertEquals(
            R.string.camera_scan_card_no_valid_number,
            vm.uiState.value.cardErrorRes
        )

        vm.onCardFrameText("CARD 4111 1111 1111 1112")
        assertEquals(1, fake.extractCalls)
    }

    @Test
    fun `hallucinated candidate absent from OCR text is rejected`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = visa)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD 5500 0000 5555 5559")

        assertNull(vm.uiState.value.cardNumber)
        assertEquals(
            R.string.camera_scan_card_no_valid_number,
            vm.uiState.value.cardErrorRes
        )
    }

    @Test
    fun `model failure reports a recognition error and recovers after rescan`() = runTest {
        val fake = FakeCardNumberRecognizer(failure = IllegalStateException("boom"))
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD $visa")

        assertEquals(
            R.string.camera_scan_card_recognition_failed,
            vm.uiState.value.cardErrorRes
        )

        vm.rescanCard()
        fake.failure = null
        fake.candidate = visa
        vm.onCardFrameText("CARD $visa")

        assertEquals(visa, vm.uiState.value.cardNumber)
        assertNull(vm.uiState.value.cardErrorRes)
    }

    // ── Frame dropping and rescan ────────────────────────────

    @Test
    fun `frames are dropped while a request is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeCardNumberRecognizer(candidate = visa, gate = gate)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD $visa")
        vm.onCardFrameText("CARD $visa")

        assertTrue(vm.uiState.value.isCardAnalyzing)
        assertEquals(1, fake.extractCalls)

        gate.complete(Unit)
        assertEquals(visa, vm.uiState.value.cardNumber)
        assertFalse(vm.uiState.value.isCardAnalyzing)
    }

    @Test
    fun `rescan clears the PAN and drops a stale in-flight result`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeCardNumberRecognizer(candidate = visa, gate = gate)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("CARD $visa")
        vm.rescanCard()
        gate.complete(Unit)

        assertNull(vm.uiState.value.cardNumber)
        assertNull(vm.uiState.value.cardErrorRes)
    }

    @Test
    fun `losing model availability clears an existing result`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = visa)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)
        vm.onCardFrameText("CARD $visa")
        assertEquals(visa, vm.uiState.value.cardNumber)

        fake.status = CardModelStatus.UNAVAILABLE
        vm.checkCardModelAvailability()

        assertEquals(CardModelStatus.UNAVAILABLE, vm.uiState.value.cardModelStatus)
        assertNull(vm.uiState.value.cardNumber)
        assertNull(vm.uiState.value.cardErrorRes)
    }

    @Test
    fun `switching back to QR mode stops accepting card frames`() = runTest {
        val fake = FakeCardNumberRecognizer(candidate = visa)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)
        vm.setMode(CameraScanMode.QR)

        vm.onCardFrameText("CARD $visa")

        assertEquals(0, fake.extractCalls)
    }

    // ── Local-only fallback ──────────────────────────────────

    @Test
    fun `local-only mode extracts the PAN from OCR without the model`() = runTest {
        val fake = FakeCardNumberRecognizer(status = CardModelStatus.LOCAL_ONLY)
        val vm = viewModel(fake)

        vm.setMode(CameraScanMode.CARD)
        assertEquals(CardModelStatus.LOCAL_ONLY, vm.uiState.value.cardModelStatus)

        vm.onCardFrameText("CARD 4111 1111 1111 1111 EXP 12/30")

        assertEquals(visa, vm.uiState.value.cardNumber)
        assertNull(vm.uiState.value.cardErrorRes)
        assertEquals(0, fake.extractCalls)
    }

    @Test
    fun `local-only mode reports no valid number when OCR has none`() = runTest {
        val fake = FakeCardNumberRecognizer(status = CardModelStatus.LOCAL_ONLY)
        val vm = viewModel(fake)
        vm.setMode(CameraScanMode.CARD)

        vm.onCardFrameText("no card here")

        assertNull(vm.uiState.value.cardNumber)
        assertEquals(
            R.string.camera_scan_card_no_valid_number,
            vm.uiState.value.cardErrorRes
        )
    }

    @Test
    fun `local-only mode is not rechecked when re-entering card mode`() = runTest {
        val fake = FakeCardNumberRecognizer(status = CardModelStatus.LOCAL_ONLY)
        val vm = viewModel(fake)

        vm.setMode(CameraScanMode.CARD)
        vm.setMode(CameraScanMode.QR)
        vm.setMode(CameraScanMode.CARD)

        assertEquals(1, fake.availabilityCalls)
        assertEquals(CardModelStatus.LOCAL_ONLY, vm.uiState.value.cardModelStatus)
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Test
    fun `clearing the ViewModel closes the GenAI client and clears the PAN`() {
        val fake = FakeCardNumberRecognizer(candidate = visa)
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, CameraScanViewModel.Factory { fake })
        val vm = provider[CameraScanViewModel::class.java]
        vm.setMode(CameraScanMode.CARD)
        vm.onCardFrameText("CARD $visa")

        store.clear()

        assertTrue(fake.closed)
        assertNull(vm.uiState.value.cardNumber)
    }
}
