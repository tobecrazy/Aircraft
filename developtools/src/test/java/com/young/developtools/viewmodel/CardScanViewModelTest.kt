package com.young.developtools.viewmodel

import com.young.developtools.data.CardType
import com.young.developtools.data.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardScanViewModelTest {

    private fun frontLines(idNumber: String = "11010519491231002X"): List<OcrLine> = listOf(
        OcrLine("姓名 张三", top = 0, bottom = 20),
        OcrLine("性别 女 民族 汉", top = 30, bottom = 50),
        OcrLine("出生 1949年12月31日", top = 60, bottom = 80),
        OcrLine("住址 北京市朝阳区某某路1号", top = 90, bottom = 110),
        OcrLine("公民身份号码 $idNumber", top = 120, bottom = 140),
    )

    private fun backLines(validity: String = "2013.05.01-2033.05.01"): List<OcrLine> = listOf(
        OcrLine("签发机关 北京市公安局朝阳分局", top = 0, bottom = 20),
        OcrLine("有效期限 $validity", top = 30, bottom = 50),
    )

    @Test
    fun `first luhn valid card locks the result`() {
        val viewModel = CardScanViewModel()

        viewModel.onTextRecognized("4242 4242 4242 4242")
        assertNull(viewModel.uiState.value.bankCard)
        viewModel.onTextRecognized("4242 4242 4242 4242")
        assertNull(viewModel.uiState.value.bankCard)
        viewModel.onTextRecognized("4242 4242 4242 4242")

        assertEquals("4242424242424242", viewModel.uiState.value.bankCard?.number)
    }

    @Test
    fun `text without a valid card does not lock`() {
        val viewModel = CardScanViewModel()

        viewModel.onTextRecognized("4242 4242 4242 4241")
        viewModel.onTextRecognized("hello")

        assertNull(viewModel.uiState.value.bankCard)
    }

    @Test
    fun `reset clears the locked card`() {
        val viewModel = CardScanViewModel()
        repeat(3) { viewModel.onTextRecognized("378282246310005") }

        viewModel.resetScan()
        assertNull(viewModel.uiState.value.bankCard)

        repeat(3) { viewModel.onTextRecognized("4242424242424242") }
        assertEquals("4242424242424242", viewModel.uiState.value.bankCard?.number)
    }

    @Test
    fun `lens toggles and rear falls back`() {
        val viewModel = CardScanViewModel()
        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)

        viewModel.toggleLens()
        assertEquals(ScanLens.FRONT, viewModel.uiState.value.lens)

        viewModel.useRearLens()
        assertEquals(ScanLens.BACK, viewModel.uiState.value.lens)
    }

    @Test
    fun `id front locks after three agreeing frames`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_FRONT)

        repeat(2) { viewModel.onTextRecognized("", frontLines()) }
        assertNull(viewModel.uiState.value.idFront)

        viewModel.onTextRecognized("", frontLines())

        val front = viewModel.uiState.value.idFront
        assertNotNull(front)
        assertEquals("11010519491231002X", front?.idNumber)
        assertEquals("张三", front?.name)
        assertEquals("女", front?.gender)
        assertEquals("汉", front?.ethnicity)
        assertEquals("1949-12-31", front?.birth)
        assertEquals("北京市朝阳区某某路1号", front?.address)
    }

    @Test
    fun `id front with a bad checksum never locks`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_FRONT)

        repeat(5) { viewModel.onTextRecognized("", frontLines("110105194912310021")) }

        assertNull(viewModel.uiState.value.idFront)
    }

    @Test
    fun `id back locks after three agreeing frames`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_BACK)

        repeat(3) { viewModel.onTextRecognized("", backLines()) }

        val back = viewModel.uiState.value.idBack
        assertNotNull(back)
        assertEquals("北京市公安局朝阳分局", back?.issuer)
        assertEquals("2013.05.01", back?.validFrom)
        assertEquals("2033.05.01", back?.validTo)
    }

    @Test
    fun `switching card type clears the previous result`() {
        val viewModel = CardScanViewModel()
        repeat(3) { viewModel.onTextRecognized("4242424242424242") }
        assertNotNull(viewModel.uiState.value.bankCard)

        viewModel.selectCardType(CardType.ID_FRONT)

        assertNull(viewModel.uiState.value.bankCard)
        assertEquals(CardType.ID_FRONT, viewModel.uiState.value.cardType)
    }
}
