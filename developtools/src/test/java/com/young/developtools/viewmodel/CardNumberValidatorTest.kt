package com.young.developtools.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardNumberValidatorTest {

    private val visa = "4111111111111111"
    private val mastercard = "5500005555555559"
    private val amex = "378282246310005"
    private val nineteenDigits = "4000000000000000006"

    // ── Normalization ────────────────────────────────────────

    @Test
    fun `digitsOf keeps ascii digits and drops separators`() {
        assertEquals(visa, CardNumberValidator.digitsOf("4111 1111-1111 1111"))
    }

    @Test
    fun `digitsOf drops letters and punctuation`() {
        assertEquals("1234", CardNumberValidator.digitsOf("ab 1.2,3:4 xyz"))
    }

    @Test
    fun `digitsOf returns empty for text without digits`() {
        assertEquals("", CardNumberValidator.digitsOf("no card here"))
    }

    // ── Length checks ────────────────────────────────────────

    @Test
    fun `twelve digits is the minimum accepted length`() {
        assertTrue(CardNumberValidator.hasValidLength("400000000002"))
    }

    @Test
    fun `nineteen digits is the maximum accepted length`() {
        assertTrue(CardNumberValidator.hasValidLength(nineteenDigits))
    }

    @Test
    fun `eleven digits is too short`() {
        assertFalse(CardNumberValidator.hasValidLength("40000000000"))
    }

    @Test
    fun `twenty digits is too long`() {
        assertFalse(CardNumberValidator.hasValidLength("40000000000000000060"))
    }

    @Test
    fun `validate rejects too short and too long candidates`() {
        assertNull(CardNumberValidator.validate("4000 0000 000", "40000000000"))
        assertNull(CardNumberValidator.validate("40000000000000000060", "40000000000000000060"))
    }

    // ── Luhn checksum ────────────────────────────────────────

    @Test
    fun `luhn accepts valid 16 digit numbers`() {
        assertTrue(CardNumberValidator.passesLuhn(visa))
        assertTrue(CardNumberValidator.passesLuhn(mastercard))
    }

    @Test
    fun `luhn accepts valid 15 and 19 digit numbers`() {
        assertTrue(CardNumberValidator.passesLuhn(amex))
        assertTrue(CardNumberValidator.passesLuhn(nineteenDigits))
    }

    @Test
    fun `luhn rejects a single altered digit`() {
        assertFalse(CardNumberValidator.passesLuhn("4111111111111112"))
    }

    @Test
    fun `luhn rejects non digit input`() {
        assertFalse(CardNumberValidator.passesLuhn("4111-1111-1111-1111"))
        assertFalse(CardNumberValidator.passesLuhn(""))
    }

    @Test
    fun `validate rejects a number that fails luhn`() {
        assertNull(CardNumberValidator.validate("4111 1111 1111 1112", "4111111111111112"))
    }

    // ── OCR-source matching ──────────────────────────────────

    @Test
    fun `validate accepts a candidate copied from OCR text with separators`() {
        val ocr = "CARD 4111-1111 1111 1111 VALID THRU 12/30"
        assertEquals(visa, CardNumberValidator.validate(ocr, "4111 1111 1111 1111"))
    }

    @Test
    fun `validate rejects an invented number absent from OCR text`() {
        val ocr = "CARD 5500 0000 5555 5559 VALID THRU 12/30"
        assertNull(CardNumberValidator.validate(ocr, visa))
    }

    @Test
    fun `validate rejects a candidate that only shares a prefix with OCR text`() {
        val ocr = "CARD 4111 1111 1111 1111"
        assertNull(CardNumberValidator.validate(ocr, "4111111111111119"))
    }

    @Test
    fun `validate rejects a null or blank candidate`() {
        assertNull(CardNumberValidator.validate(visa, null))
        assertNull(CardNumberValidator.validate(visa, ""))
        assertNull(CardNumberValidator.validate(visa, "   "))
    }

    @Test
    fun `validate rejects when OCR text has no digits`() {
        assertNull(CardNumberValidator.validate("no card here", visa))
    }

    // ── Local OCR extraction ─────────────────────────────────

    @Test
    fun `extract finds a valid number with separators`() {
        assertEquals(
            visa,
            CardNumberValidator.extract("CARD 4111 1111 1111 1111 EXP 12/30")
        )
    }

    @Test
    fun `extract finds a number embedded in OCR noise`() {
        assertEquals(
            visa,
            CardNumberValidator.extract("XXX4111-1111-1111-1111YYY")
        )
    }

    @Test
    fun `extract tolerates repeated separators`() {
        assertEquals(
            visa,
            CardNumberValidator.extract("CARD 4111   1111  1111   1111")
        )
    }

    @Test
    fun `extract skips an invalid luhn sequence and returns the next valid one`() {
        assertEquals(
            mastercard,
            CardNumberValidator.extract("4111111111111112 then 5500005555555559")
        )
    }

    @Test
    fun `extract accepts the minimum and maximum lengths`() {
        assertEquals("400000000002", CardNumberValidator.extract("4000 0000 0002"))
        assertEquals(nineteenDigits, CardNumberValidator.extract(nineteenDigits))
    }

    @Test
    fun `extract rejects a twenty digit sequence`() {
        assertNull(CardNumberValidator.extract("40000000000000000060"))
    }

    @Test
    fun `extract ignores expiry and cvv digits`() {
        assertNull(CardNumberValidator.extract("EXP 12/30 CVV 123"))
    }

    @Test
    fun `extract does not join numbers split across lines`() {
        assertNull(CardNumberValidator.extract("4111 1111\n1111 1111"))
    }

    // ── Masking ──────────────────────────────────────────────

    @Test
    fun `mask hides all but the trailing group of a 16 digit number`() {
        assertEquals("•••• •••• •••• 1111", CardNumberValidator.mask(visa))
    }

    @Test
    fun `mask keeps the trailing group of a 15 digit number`() {
        assertEquals("•••• •••• •••• 005", CardNumberValidator.mask(amex))
    }

    @Test
    fun `mask normalizes separators before grouping`() {
        assertEquals(
            CardNumberValidator.mask(visa),
            CardNumberValidator.mask("4111-1111 1111-1111")
        )
    }

    @Test
    fun `mask returns empty for empty input`() {
        assertEquals("", CardNumberValidator.mask(""))
    }
}
