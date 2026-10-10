package com.young.developtools.utils.apidebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderValidatorTest {

    @Test
    fun `valid headers produce no errors`() {
        val errors = validateHeaders("Content-Type: application/json\nAuthorization: Bearer abc")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `blank lines are skipped`() {
        val errors = validateHeaders("\nContent-Type: application/json\n\n")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `line without colon is reported with line number`() {
        val errors = validateHeaders("Content-Type: application/json\njust some text")
        assertEquals(listOf(HeaderError(2, HeaderErrorKind.NO_COLON)), errors)
    }

    @Test
    fun `empty key is reported`() {
        val errors = validateHeaders(": application/json")
        assertEquals(listOf(HeaderError(1, HeaderErrorKind.EMPTY_KEY)), errors)
    }

    @Test
    fun `key with spaces is invalid`() {
        val errors = validateHeaders("My Header: value")
        assertEquals(listOf(HeaderError(1, HeaderErrorKind.INVALID_KEY)), errors)
    }

    @Test
    fun `empty value is allowed`() {
        val errors = validateHeaders("X-Flag:")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `value containing colons is fine`() {
        val errors = validateHeaders("X-Time: 12:30:00")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `parseHeaders skips invalid lines`() {
        val map = parseHeaders("Content-Type: application/json\nbroken line\nX-A: 1")
        assertEquals(mapOf("Content-Type" to "application/json", "X-A" to "1"), map)
    }
}
