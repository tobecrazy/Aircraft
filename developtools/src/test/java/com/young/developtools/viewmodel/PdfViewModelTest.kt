package com.young.developtools.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewModelTest {

    @Test
    fun `render width scales with the settled zoom`() {
        assertEquals(1000, renderWidthFor(1000, 1f))
        assertEquals(2000, renderWidthFor(1000, 2f))
    }

    @Test
    fun `render width is clamped to the page safe ceiling`() {
        assertEquals(MAX_RENDER_WIDTH_PX, renderWidthFor(2000, 5f))
        assertEquals(1, renderWidthFor(0, 1f))
    }

    @Test
    fun `pdf header is recognised`() {
        assertTrue(hasPdfHeader("%PDF-1.7".toByteArray()))
        assertTrue(hasPdfHeader(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)))
    }

    @Test
    fun `non pdf payloads are rejected`() {
        assertFalse(hasPdfHeader("PK".toByteArray()))
        assertFalse(hasPdfHeader(byteArrayOf(0x25.toByte())))
        assertFalse(hasPdfHeader(ByteArray(0)))
    }
}