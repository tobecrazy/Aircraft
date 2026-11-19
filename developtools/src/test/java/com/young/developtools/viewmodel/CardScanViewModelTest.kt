package com.young.developtools.viewmodel

import com.young.developtools.data.CardType
import com.young.developtools.data.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScanViewModelTest {

    @Test
    fun `bank card rotation sweep covers arbitrary angles`() {
        val angles = BANK_CARD_ROTATION_ANGLES.sorted()

        assertEquals(72, angles.size)
        assertEquals(0f, angles.first())
        assertTrue(angles.zipWithNext().all { (current, next) -> next - current <= 5f })
    }

    @Test
    fun `gallery rotation sweep uses coarse angles`() {
        val angles = BANK_CARD_GALLERY_ROTATION_ANGLES.sorted()

        assertEquals(24, angles.size)
        assertTrue(angles.zipWithNext().all { (current, next) -> next - current <= 15f })
    }

    @Test
    fun `gallery card candidate needs two angles or independent image passes`() {
        val confirmer = BankCardRotationConfirmer()
        val text = "4242424242424242"

        assertTrue(confirmer.observe(text, 0f, BankCardOcrPass.FULL).isEmpty())
        assertTrue(confirmer.observe(text, 0f, BankCardOcrPass.FULL).isEmpty())
        assertEquals(
            "4242424242424242",
            confirmer.observe(text, 90f, BankCardOcrPass.FULL).single().number,
        )

        val bandConfirmer = BankCardRotationConfirmer()
        assertTrue(bandConfirmer.observe(text, 0f, BankCardOcrPass.FULL).isEmpty())
        assertEquals(
            "4242424242424242",
            bandConfirmer.observe(text, 0f, BankCardOcrPass.NUMBER_BAND).single().number,
        )
    }

    @Test
    fun `reflective card OCR accepts a valid result from either pass`() {
        assertEquals(
            "4242424242424242",
            matchingBankCardText("4242 4242 4242 4242", "4242 4242 4242 4242"),
        )
        assertEquals(
            "4242424242424242",
            matchingBankCardText("", "4242 4242 4242 4242"),
        )
    }

    private fun portraitLines(idNumber: String = "11010519491231002X"): List<OcrLine> = listOf(
        OcrLine("姓名 张三", top = 0, bottom = 20),
        OcrLine("性别 女 民族 汉", top = 30, bottom = 50),
        OcrLine("出生 1949年12月31日", top = 60, bottom = 80),
        OcrLine("住址 北京市朝阳区某某路1号", top = 90, bottom = 110),
        OcrLine("公民身份号码 $idNumber", top = 120, bottom = 140),
    )

    private fun emblemLines(validity: String = "2013.05.01-2033.05.01"): List<OcrLine> = listOf(
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
    fun `id card mode auto-detects the portrait side`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_CARD)

        repeat(2) { viewModel.onTextRecognized("", portraitLines()) }
        assertNull(viewModel.uiState.value.idPortrait)

        viewModel.onTextRecognized("", portraitLines())

        val portrait = viewModel.uiState.value.idPortrait
        assertNotNull(portrait)
        assertEquals("11010519491231002X", portrait?.idNumber)
        assertEquals("张三", portrait?.name)
        assertEquals("女", portrait?.gender)
        assertEquals("汉", portrait?.ethnicity)
        assertEquals("1949-12-31", portrait?.birth)
        assertEquals("北京市朝阳区某某路1号", portrait?.address)
        assertNull(viewModel.uiState.value.idEmblem)
    }

    @Test
    fun `id card mode auto-detects the emblem side`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_CARD)

        repeat(3) { viewModel.onTextRecognized("", emblemLines()) }

        val emblem = viewModel.uiState.value.idEmblem
        assertNotNull(emblem)
        assertEquals("北京市公安局朝阳分局", emblem?.issuer)
        assertEquals("2013.05.01", emblem?.validFrom)
        assertEquals("2033.05.01", emblem?.validTo)
        assertNull(viewModel.uiState.value.idPortrait)
    }

    @Test
    fun `id card portrait with a bad checksum never locks`() {
        val viewModel = CardScanViewModel()
        viewModel.selectCardType(CardType.ID_CARD)

        repeat(5) { viewModel.onTextRecognized("", portraitLines("110105194912310021")) }

        assertNull(viewModel.uiState.value.idPortrait)
        // The unreadable number must not be mistaken for the emblem side either.
        assertNull(viewModel.uiState.value.idEmblem)
    }

    @Test
    fun `switching card type clears the previous result`() {
        val viewModel = CardScanViewModel()
        repeat(3) { viewModel.onTextRecognized("4242424242424242") }
        assertNotNull(viewModel.uiState.value.bankCard)

        viewModel.selectCardType(CardType.ID_CARD)

        assertNull(viewModel.uiState.value.bankCard)
        assertEquals(CardType.ID_CARD, viewModel.uiState.value.cardType)
    }
}
