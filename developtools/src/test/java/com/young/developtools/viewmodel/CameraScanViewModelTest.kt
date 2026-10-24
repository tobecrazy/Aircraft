package com.young.developtools.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraScanViewModelTest {

    @Test
    fun `lens starts on the rear camera and toggles both ways`() {
        val viewModel = CameraScanViewModel()

        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)

        viewModel.toggleLens()
        assertEquals(ScanLens.FRONT, viewModel.uiState.value.lens)

        viewModel.toggleLens()
        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)
    }

    @Test
    fun `useRearLens recovers after the front lens fails to bind`() {
        val viewModel = CameraScanViewModel()
        viewModel.toggleLens()

        viewModel.useRearLens()

        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)
    }

    @Test
    fun `first barcode wins until the scan is reset`() {
        val viewModel = CameraScanViewModel()

        viewModel.onBarcodeDetected("first")
        viewModel.onBarcodeDetected("second")
        assertEquals("first", viewModel.uiState.value.result)

        viewModel.resetScan()
        assertNull(viewModel.uiState.value.result)

        viewModel.onBarcodeDetected("third")
        assertEquals("third", viewModel.uiState.value.result)
    }

    @Test
    fun `blank payloads are ignored`() {
        val viewModel = CameraScanViewModel()

        viewModel.onBarcodeDetected("   ")

        assertNull(viewModel.uiState.value.result)
    }
}
