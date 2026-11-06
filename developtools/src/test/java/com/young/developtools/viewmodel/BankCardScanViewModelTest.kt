package com.young.developtools.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankCardScanViewModelTest {

    @Test
    fun `first luhn valid card locks the result`() {
        val viewModel = BankCardScanViewModel()

        viewModel.onTextRecognized("4242 4242 4242 4242")
        viewModel.onTextRecognized("5555 5555 5555 4444")

        assertEquals("4242424242424242", viewModel.uiState.value.card?.number)
        assertEquals(1, viewModel.uiState.value.cards.size)
    }

    @Test
    fun `text without a valid card does not lock`() {
        val viewModel = BankCardScanViewModel()

        viewModel.onTextRecognized("4242 4242 4242 4241")
        viewModel.onTextRecognized("hello")

        assertNull(viewModel.uiState.value.card)
    }

    @Test
    fun `reset clears the locked card`() {
        val viewModel = BankCardScanViewModel()
        viewModel.onTextRecognized("378282246310005")

        viewModel.resetScan()
        assertNull(viewModel.uiState.value.card)

        viewModel.onTextRecognized("4242424242424242")
        assertEquals("4242424242424242", viewModel.uiState.value.card?.number)
    }

    @Test
    fun `lens toggles and rear falls back`() {
        val viewModel = BankCardScanViewModel()
        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)

        viewModel.toggleLens()
        assertEquals(ScanLens.FRONT, viewModel.uiState.value.lens)

        viewModel.useRearLens()
        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)
    }
}
