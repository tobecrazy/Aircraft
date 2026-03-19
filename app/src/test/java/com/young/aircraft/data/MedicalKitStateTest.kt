package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class MedicalKitStateTest {

    @Test
    fun `collected defaults to false`() {
        val state = MedicalKitState(x = 100f, y = 100f, spawnFrame = 0, bitmapIndex = 0)
        assertFalse(state.collected)
    }

    @Test
    fun `collected can be set to true`() {
        val state = MedicalKitState(x = 100f, y = 100f, spawnFrame = 0, bitmapIndex = 0)
        state.collected = true
        assertTrue(state.collected)
    }

    @Test
    fun `bitmapIndex is stored correctly`() {
        val state = MedicalKitState(x = 100f, y = 100f, spawnFrame = 0, bitmapIndex = 1)
        assertEquals(1, state.bitmapIndex)
    }

    @Test
    fun `spawnFrame is stored correctly`() {
        val state = MedicalKitState(x = 100f, y = 100f, spawnFrame = 300, bitmapIndex = 0)
        assertEquals(300, state.spawnFrame)
    }
}
