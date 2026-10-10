package com.young.aircraft.utils.apidebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiRequestValidatorTest {

    @Test
    fun `blank url is EMPTY`() {
        assertEquals(UrlError.EMPTY, validateUrl("   "))
    }

    @Test
    fun `valid https url passes`() {
        assertNull(validateUrl("https://example.com/api/endpoint"))
    }

    @Test
    fun `non-http scheme is INVALID`() {
        assertEquals(UrlError.INVALID, validateUrl("ftp://example.com/file"))
    }

    @Test
    fun `malformed url is INVALID`() {
        assertEquals(UrlError.INVALID, validateUrl("not a url"))
        assertEquals(UrlError.INVALID, validateUrl("https://"))
    }

    @Test
    fun `blank body is always fine`() {
        assertNull(validateBody("POST", ""))
        assertNull(validateBody("GET", ""))
    }

    @Test
    fun `valid json body passes for POST`() {
        assertNull(validateBody("POST", "{\"key\": \"value\"}"))
    }

    @Test
    fun `invalid json body fails for POST`() {
        assertEquals(BodyError.INVALID_JSON, validateBody("POST", "{not json"))
    }

    @Test
    fun `body on GET is NOT_ALLOWED`() {
        assertEquals(BodyError.NOT_ALLOWED, validateBody("GET", "{\"key\": 1}"))
    }
}
