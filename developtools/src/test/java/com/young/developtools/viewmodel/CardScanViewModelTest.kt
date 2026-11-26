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
    fun `unknown IIN requires two independent passes not two rotations`() {
        val number = generatedUnknownLuhnNumber()
        val confirmer = BankCardRotationConfirmer()

        assertTrue(confirmer.observe(number, 0f, BankCardOcrPass.FULL).isEmpty())
        assertTrue(confirmer.observe(number, 180f, BankCardOcrPass.FULL).isEmpty())
        val accepted = confirmer.observe(number, 0f, BankCardOcrPass.ENHANCED_FULL).single()
        val trace = confirmer.candidateTrace(accepted)

        assertEquals(number.length, trace.length)
        assertEquals("UNKNOWN", trace.scheme)
        assertEquals(2, trace.acceptedPassCount)
        assertEquals(BankCardCandidateOrigin.CONTIGUOUS, trace.origin)
    }

    @Test
    fun `unknown IIN vote cannot be built from repeated rotations of one pass`() {
        val number = generatedUnknownLuhnNumber()
        val confirmer = BankCardRotationConfirmer()

        listOf(0f, 90f, 180f, 270f).forEach { rotation ->
            assertTrue(confirmer.observe(number, rotation, BankCardOcrPass.FULL).isEmpty())
        }

        assertTrue(confirmer.voteAllRotations().isEmpty())
    }

    @Test
    fun `number line and chinese passes corroborate a candidate`() {
        val text = "4242424242424242"

        val lineConfirmer = BankCardRotationConfirmer()
        assertTrue(lineConfirmer.observe(text, 0f, BankCardOcrPass.FULL).isEmpty())
        assertEquals(
            text,
            lineConfirmer.observe(text, 0f, BankCardOcrPass.NUMBER_LINE).single().number,
        )

        val chineseConfirmer = BankCardRotationConfirmer()
        assertTrue(chineseConfirmer.observe(text, 0f, BankCardOcrPass.ADAPTIVE_NUMBER_LINE).isEmpty())
        assertEquals(
            text,
            chineseConfirmer.observe(text, 0f, BankCardOcrPass.CHINESE_NUMBER_LINE).single().number,
        )
    }

    @Test
    fun `enhanced full image pass corroborates the raw pass`() {
        val text = "4242424242424242"
        val confirmer = BankCardRotationConfirmer()

        assertTrue(confirmer.observe(text, 0f, BankCardOcrPass.FULL).isEmpty())
        assertEquals(text, confirmer.observe(text, 0f, BankCardOcrPass.ENHANCED_FULL).single().number)
    }

    @Test
    fun `vote recovers a number that no single pass read cleanly`() {
        val confirmer = BankCardRotationConfirmer()
        listOf(
            "6222670110038083300" to BankCardOcrPass.FULL,
            "6222620110033083300" to BankCardOcrPass.NUMBER_LINE,
            "6222690110038083300" to BankCardOcrPass.ADAPTIVE_NUMBER_LINE,
            "6222620110038083300" to BankCardOcrPass.CHINESE_NUMBER_LINE,
        ).forEach { (text, pass) -> confirmer.observe(text, 0f, pass) }

        // Four readings split the disputed positions evenly; nothing is averaged.
        assertTrue(confirmer.vote(0f).isEmpty())

        confirmer.observe("6222620110038083300", 0f, BankCardOcrPass.ENHANCED_FULL)

        assertEquals("6222620110038083300", confirmer.vote(0f).single().number)
        // The vote is per rotation: another angle has no readings of its own.
        assertTrue(confirmer.vote(90f).isEmpty())
    }

    @Test
    fun `different number rows at one orientation corroborate`() {
        val confirmer = BankCardRotationConfirmer()
        val text = "4242424242424242"

        assertTrue(confirmer.observe(text, 0f, BankCardOcrPass.NUMBER_LINE).isEmpty())
        assertEquals(
            text,
            confirmer.observe(text, 0f, BankCardOcrPass.UPPER_NUMBER_LINE).single().number,
        )
    }

    @Test
    fun `repeating one pass does not corroborate itself`() {
        val confirmer = BankCardRotationConfirmer()
        val text = "4242424242424242"

        repeat(3) {
            assertTrue(confirmer.observe(text, 0f, BankCardOcrPass.NUMBER_LINE).isEmpty())
        }
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

    private fun generatedUnknownLuhnNumber(): String {
        val body = "999999999999999"
        return body + (0..9).first {
            com.young.developtools.data.isLuhnValid(body + it)
        }
    }

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
