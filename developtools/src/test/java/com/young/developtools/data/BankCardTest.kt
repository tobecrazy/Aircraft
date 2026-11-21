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
    fun `parse accepts valid cards across networks and pan lengths`() {
        val numbers = listOf(
            "30569309025904",
            "3530111333300000",
            "6222021616004849669",
        )

        numbers.forEach { number ->
            assertTrue("expected Luhn-valid test number: $number", isLuhnValid(number))
            assertEquals(number, firstBankCard(number)?.number)
        }
    }

    @Test
    fun `parse ignores luhn failures and short digit runs`() {
        assertTrue(parseBankCards("4242 4242 4242 4241").isEmpty())
        assertTrue(parseBankCards("1234567890").isEmpty())
        assertTrue(parseBankCards("").isEmpty())
        assertNull(firstBankCard("no digits here"))
    }

    @Test
    fun `parse rejects short unknown-network Luhn candidates`() {
        val partialUnknown = "99999999999993"

        assertTrue(isLuhnValid(partialUnknown))
        assertTrue(parseBankCards(partialUnknown).isEmpty())
        assertEquals("30569309025904", firstBankCard("30569309025904")?.number)
        assertEquals(BankCardScheme.DINERS, firstBankCard("30569309025904")?.scheme)
    }

    @Test
    fun `parse rejects a luhn-valid partial known-network number`() {
        val partial = "555555555559"

        assertTrue(isLuhnValid(partial))
        assertTrue(parseBankCards("5555 5555 5555 9").isEmpty())
    }

    @Test
    fun `parse prefers longest card and de duplicates`() {
        val cards = parseBankCards("4242 4242 4242 4242 and 4242424242424242 again")
        assertEquals(1, cards.size)
    }

    @Test
    fun `parse recovers a number with an o-or-l confusion inside the run`() {
        // ML Kit reads an embossed 1 as "l" and an embossed 0 as "O". The card
        // must contain those digits, otherwise the confusion cannot be undone.
        val lConfusion = parseBankCards("4111 1111 1111 11l1")
        assertEquals(1, lConfusion.size)
        assertEquals("4111111111111111", lConfusion[0].number)

        val oConfusion = parseBankCards("4O00 0000 0000 0002")
        assertEquals(1, oConfusion.size)
        assertEquals("4000000000000002", oConfusion[0].number)
    }

    @Test
    fun `parse recovers a lowercase b misread for a six`() {
        assertEquals("6221260000000000", firstBankCard("b221 2600 0000 0000")?.number)
    }

    @Test
    fun `parse keeps repaired candidates when another clean candidate exists`() {
        val cards = parseBankCards("4242 4242 4242 4242 and 4O00 0000 0000 0002")

        assertEquals(
            setOf("4242424242424242", "4000000000000002"),
            cards.map { it.number }.toSet()
        )
    }

    @Test
    fun `parse windows a 20-digit blob down to the embedded card`() {
        // A trailing expiry fragment glued onto the card makes a 20-digit run;
        // the true 16-digit card must still surface.
        val cards = parseBankCards("4242424242424242 9999")
        assertEquals("4242424242424242", cards.firstOrNull()?.number)
    }

    @Test
    fun `parse does not glue nearby numbers onto the card`() {
        // Joined digit stream would be 19 digits "4242424242424242121", which
        // Luhn-passes and would sort ahead of the real 16-digit card.
        val cards = parseBankCards("CARD 4242 4242 4242 4242 CVV 121")
        assertEquals(1, cards.size)
        assertEquals("4242424242424242", cards[0].number)
    }

    @Test
    fun `vote repairs a long number that no single reading got right`() {
        // UnionPay 6222620110038083300 is Luhn-valid; two readings substitute a
        // digit in a different position, so neither parses on its own.
        val readings = listOf(
            "6222670110038083300",
            "6222620110033083300",
            "6222620110038083300",
            "6222620110038083300",
        )

        assertEquals("6222620110038083300", voteCardReadings(readings))
        assertTrue(parseBankCards(readings[0]).isEmpty())
        assertTrue(parseBankCards(readings[1]).isEmpty())
    }

    @Test
    fun `vote never averages unrelated readings into a card`() {
        // Each is a different misread; a per-position majority would synthesize
        // 4242424242424242, which none of the three ever read.
        assertNull(
            voteCardReadings(
                listOf(
                    "4252424242424242",
                    "4342424242424242",
                    "5242424242424242",
                )
            )
        )
    }

    @Test
    fun `vote only considers the largest cluster of readings`() {
        // Two clusters of two; neither reaches the minimum cluster size.
        assertNull(
            voteCardReadings(
                listOf(
                    "6222620110038083300",
                    "6222620110038083301",
                    "6222629999999999999",
                    "6222629999999999998",
                )
            )
        )
    }

    @Test
    fun `vote needs a strict majority in every position`() {
        val readings = listOf(
            "6222620110038083300",
            "6222620110033083300",
            "6222670110038083300",
            "6222670110033083300",
        )

        assertNull(voteCardReadings(readings))
        // Fewer than four readings is not a vote; the exact-match path handles those.
        assertNull(voteCardReadings(readings.take(3)))
    }

    @Test
    fun `vote rejects a merged number that fails luhn`() {
        val invalid = "4242424242424241"

        assertNull(voteCardReadings(listOf(invalid, invalid, invalid)))
    }

    @Test
    fun `card number runs keep near misses and skip other lengths`() {
        assertEquals(
            listOf("6222620110038083300"),
            cardNumberRuns("6222 6201 1003 8083 300 exp 12/24"),
        )
        assertTrue(cardNumberRuns("4242 4242").isEmpty())
    }
}
