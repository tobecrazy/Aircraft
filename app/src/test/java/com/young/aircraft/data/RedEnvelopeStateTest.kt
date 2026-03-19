package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class RedEnvelopeStateTest {

    @Test
    fun `isDetonated returns false when hitPoints is positive`() {
        val state = RedEnvelopeState(x = 100f, y = 100f, hitPoints = 3)
        assertFalse(state.isDetonated())
    }

    @Test
    fun `isDetonated returns false when hitPoints is zero`() {
        val state = RedEnvelopeState(x = 100f, y = 100f, hitPoints = 0)
        assertTrue(state.isDetonated())
    }

    @Test
    fun `isDetonated returns true when hitPoints is negative`() {
        val state = RedEnvelopeState(x = 100f, y = 100f, hitPoints = -1)
        assertTrue(state.isDetonated())
    }

    @Test
    fun `isExpired returns false when destroyedTime is zero`() {
        val state = RedEnvelopeState(x = 100f, y = 100f, destroyedTime = 0L)
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns false when destroyedTime is recent`() {
        val state = RedEnvelopeState(x = 100f, y = 100f, destroyedTime = System.currentTimeMillis())
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns true when destroyedTime is older than 500ms`() {
        val oldTime = System.currentTimeMillis() - 600L
        val state = RedEnvelopeState(x = 100f, y = 100f, destroyedTime = oldTime)
        assertTrue(state.isExpired())
    }

    @Test
    fun `default hitPoints is 3`() {
        val state = RedEnvelopeState(x = 100f, y = 100f)
        assertEquals(3, state.hitPoints)
    }
}
