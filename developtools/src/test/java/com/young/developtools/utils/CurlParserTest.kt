package com.young.developtools.utils.apidebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CurlParserTest {

    @Test
    fun `simple GET with bare url`() {
        val result = parseCurl("curl https://example.com/api")
        assertEquals("https://example.com/api", result.url)
        assertEquals("GET", result.method)
        assertTrue(result.headers.isEmpty())
        assertNull(result.body)
    }

    @Test
    fun `explicit method and headers`() {
        val result = parseCurl("curl -X POST -H 'Content-Type: application/json' -d '{\"a\":1}' https://example.com/api")
        assertEquals("POST", result.method)
        assertEquals(listOf("Content-Type" to "application/json"), result.headers)
        assertEquals("{\"a\":1}", result.body)
    }

    @Test
    fun `data flag implies POST`() {
        val result = parseCurl("curl --data-raw 'a=1' https://example.com/api")
        assertEquals("POST", result.method)
        assertEquals("a=1", result.body)
    }

    @Test
    fun `multiple data flags are joined with ampersand`() {
        val result = parseCurl("curl -d 'a=1' -d 'b=2' https://example.com/api")
        assertEquals("a=1&b=2", result.body)
    }

    @Test
    fun `header value with colons and inner double quotes is preserved`() {
        val result = parseCurl(
            "curl -H 'User-Agent: {\"appName\":\"SF\", \"v\":\"1\"}' " +
                "-H 'traceparent: 00-abc-def-01' https://example.com/api"
        )
        assertEquals(
            listOf(
                "User-Agent" to "{\"appName\":\"SF\", \"v\":\"1\"}",
                "traceparent" to "00-abc-def-01"
            ),
            result.headers
        )
    }

    @Test
    fun `real world geoip curl is parsed`() {
        val result = parseCurl(
            "curl -X GET -H 'traceparent: 00-083b6202d5bb92ed0b48d4a805de5e99-91761f8b364806b3-01' " +
                "-H 'Referer: https://qapatchpreview.hcm.ondemand.com/' " +
                "-H 'Authorization: Bearer eyJ0b2tlbkNvbnRlbnQ' " +
                "-H 'Accept-Language: en-CN;q=1, zh-Hans-CN;q=0.9' " +
                "-H 'User-Agent: {\"appName\":\"SuccessFactors\", \"appVersion\":\"18.0.0 rv:3\"}' " +
                "'https://dc25patchpreview-mobile.hcm.ondemand.com/api/v1/geoip/status'"
        )
        assertEquals("GET", result.method)
        assertEquals("https://dc25patchpreview-mobile.hcm.ondemand.com/api/v1/geoip/status", result.url)
        assertEquals(5, result.headers.size)
        assertEquals("00-083b6202d5bb92ed0b48d4a805de5e99-91761f8b364806b3-01", result.headers[0].second)
        assertEquals("{\"appName\":\"SuccessFactors\", \"appVersion\":\"18.0.0 rv:3\"}", result.headers[4].second)
        assertNull(result.body)
    }

    @Test
    fun `missing url throws`() {
        try {
            parseCurl("curl -X GET -H 'A: b'")
            fail("expected CurlParseException")
        } catch (e: CurlParseException) {
            // expected
        }
    }

    @Test
    fun `header without colon throws`() {
        try {
            parseCurl("curl -H 'Broken' https://example.com")
            fail("expected CurlParseException")
        } catch (e: CurlParseException) {
            // expected
        }
    }
}
