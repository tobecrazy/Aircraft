package com.young.aircraft.utils

import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataUriUtilsTest {

    private fun dataUri(mime: String, payload: ByteArray): String =
        "data:$mime;base64," + Base64.encodeToString(payload, Base64.NO_WRAP)

    @Test
    fun `isBase64DataUri detects base64 data uris`() {
        assertTrue(DataUriUtils.isBase64DataUri("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(DataUriUtils.isBase64DataUri("DATA:IMAGE/PNG;BASE64,iVBORw0KGgo="))
    }

    @Test
    fun `isBase64DataUri rejects non data uris`() {
        assertFalse(DataUriUtils.isBase64DataUri("https://example.com/a.png"))
        assertFalse(DataUriUtils.isBase64DataUri("data:image/svg+xml,<svg/>")) // not base64
        assertFalse(DataUriUtils.isBase64DataUri(""))
    }

    @Test
    fun `mimeType parsed from data uri`() {
        assertEquals("image/png", DataUriUtils.mimeType("data:image/png;base64,AAAA"))
        assertEquals("image/svg+xml", DataUriUtils.mimeType("data:image/svg+xml;base64,AAAA"))
        assertNull(DataUriUtils.mimeType("https://example.com/a.png"))
    }

    @Test
    fun `fileExtension derived from mime type`() {
        assertEquals("png", DataUriUtils.fileExtension("data:image/png;base64,AAAA"))
        assertEquals("jpg", DataUriUtils.fileExtension("data:image/jpeg;base64,AAAA"))
        assertEquals("gif", DataUriUtils.fileExtension("data:image/gif;base64,AAAA"))
        assertEquals("svg", DataUriUtils.fileExtension("data:image/svg+xml;base64,AAAA"))
        assertEquals("webp", DataUriUtils.fileExtension("data:image/webp;base64,AAAA"))
        assertEquals("png", DataUriUtils.fileExtension("https://example.com/a")) // fallback
    }

    @Test
    fun `decodeBytes round-trips the payload`() {
        val original = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val uri = dataUri("image/png", original)
        assertArrayEquals(original, DataUriUtils.decodeBytes(uri))
    }

    @Test
    fun `decodeBytes returns null for non data uri or empty payload`() {
        assertNull(DataUriUtils.decodeBytes("https://example.com/a.png"))
        assertNull(DataUriUtils.decodeBytes("data:image/png;base64,"))
    }
}
