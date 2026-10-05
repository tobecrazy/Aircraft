package com.young.aircraft.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the phone/email gate that ContactsViewModel applies before any ContentResolver write.
 * A false accept corrupts the user's address book; a false reject blocks a legitimate save.
 */
class ContactsValidationTest {

    @Test
    fun `accepts mainland mobile numbers bare and with an international prefix`() {
        assertTrue(isValidChinaPhoneNumber("13800138000"))
        assertTrue(isValidChinaPhoneNumber("+8613800138000"))
        assertTrue(isValidChinaPhoneNumber("008613800138000"))
        assertTrue(isValidChinaPhoneNumber("+86 138-0013-8000"))
    }

    @Test
    fun `accepts hong kong macau and taiwan numbers`() {
        assertTrue(isValidChinaPhoneNumber("+85223456789"))
        assertTrue(isValidChinaPhoneNumber("0085223456789"))
        assertTrue(isValidChinaPhoneNumber("+85366666666"))
        assertTrue(isValidChinaPhoneNumber("+886912345678"))
        assertTrue(isValidChinaPhoneNumber("0987654321"))
    }

    @Test
    fun `accepts mainland landlines`() {
        // The subscriber number starts 2-8; a leading 1 or 0 is not a valid CN landline exchange.
        assertTrue(isValidChinaPhoneNumber("01088888888"))
        assertTrue(isValidChinaPhoneNumber("075588888888"))
        assertTrue(isValidChinaPhoneNumber("+8602088888888"))
        assertFalse(isValidChinaPhoneNumber("01012345678"))
    }

    @Test
    fun `rejects unknown international prefixes and short numbers`() {
        assertFalse(isValidChinaPhoneNumber("+14155550100"))
        assertFalse(isValidChinaPhoneNumber("0015551234"))
        assertFalse(isValidChinaPhoneNumber("12345"))
        assertFalse(isValidChinaPhoneNumber(""))
        assertFalse(isValidChinaPhoneNumber("not a phone"))
    }

    @Test
    fun `treats a blank email as valid and trims surrounding whitespace`() {
        assertTrue(isValidOptionalEmail(""))
        assertTrue(isValidOptionalEmail("   "))
        assertTrue(isValidOptionalEmail(" someone@example.com "))
    }

    @Test
    fun `rejects emails without a dotted domain or with invalid characters`() {
        assertFalse(isValidOptionalEmail("someone@localhost"))
        assertFalse(isValidOptionalEmail("someone@example"))
        assertFalse(isValidOptionalEmail("some one@example.com"))
        assertFalse(isValidOptionalEmail("someone@.com"))
        assertFalse(isValidOptionalEmail("@example.com"))
    }
}
