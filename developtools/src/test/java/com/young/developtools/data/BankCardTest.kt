package com.young.developtools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCardTest {

    @Test
    fun `luhn accepts known valid numbers`() {
        assertTrue(isLuhnValid("4242424242424242"))
        assertTrue(isLuhnValid("378282246310005"))
        assertTrue(isLuhnValid("5555555555554444"))
        assertTrue(isLuhnValid("6011111111111117"))
        assertTrue(isLuhnValid("6221260000000000"))
    }

    @Test
    fun `luhn rejects bad check digit and non digits`() {
        assertFalse(isLuhnValid("4242424242424241"))
        assertFalse(isLuhnValid("4242a4242424242"))
        assertFalse(isLuhnValid(""))
    }

    @Test
    fun `scheme detection by prefix`() {
        assertEquals(BankCardScheme.VISA, schemeFor("4242424242424242"))
        assertEquals(BankCardScheme.MASTERCARD, schemeFor("5555555555554444"))
        assertEquals(BankCardScheme.MASTERCARD, schemeFor("2223000048400011"))
        assertEquals(BankCardScheme.AMEX, schemeFor("378282246310005"))
        assertEquals(BankCardScheme.DISCOVER, schemeFor("6011111111111117"))
        assertEquals(BankCardScheme.UNIONPAY, schemeFor("6221260000000000"))
        assertEquals(BankCardScheme.UNKNOWN, schemeFor("9999999999999995"))
    }

    @Test
    fun `format groups in four except amex 4-6-5`() {
        assertEquals("4242 4242 4242 4242", formatBankCardNumber("4242424242424242", BankCardScheme.VISA))
        assertEquals("3782 822463 10005", formatBankCardNumber("378282246310005", BankCardScheme.AMEX))
    }

    @Test
    fun `parse finds spaced card in ocr text`() {
        val cards = parseBankCards("CARD 4242 4242 4242 4242 EXP 12/28")
        assertEquals(1, cards.size)
        assertEquals("4242424242424242", cards[0].number)
        assertEquals("4242 4242 4242 4242", cards[0].formatted)
        assertEquals("4242", cards[0].last4)
        assertEquals(BankCardScheme.VISA, cards[0].scheme)
    }

    @Test
    fun `parse finds dashed and unspaced cards`() {
        assertEquals("5555555555554444", firstBankCard("5555-5555-5555-4444")?.number)
        assertEquals("378282246310005", firstBankCard("378282246310005")?.number)
    }

    @Test
    fun `parse ignores luhn failures and short digit runs`() {
        assertTrue(parseBankCards("4242 4242 4242 4241").isEmpty())
        assertTrue(parseBankCards("1234567890").isEmpty())
        assertTrue(parseBankCards("").isEmpty())
        assertNull(firstBankCard("no digits here"))
    }

    @Test
    fun `parse prefers longest card and de duplicates`() {
        val cards = parseBankCards("4242 4242 4242 4242 and 4242424242424242 again")
        assertEquals(1, cards.size)
    }

    @Test
    fun `parse does not glue nearby numbers onto the card`() {
        // Joined digit stream would be 19 digits "4242424242424242121", which
        // Luhn-passes and would sort ahead of the real 16-digit card.
        val cards = parseBankCards("CARD 4242 4242 4242 4242 CVV 121")
        assertEquals(1, cards.size)
        assertEquals("4242424242424242", cards[0].number)
    }
}
