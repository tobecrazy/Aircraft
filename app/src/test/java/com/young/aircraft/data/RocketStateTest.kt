package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class RocketStateTest {

    @Test
    fun `active defaults to true`() {
        val state = RocketState(x = 100f, y = 100f)
        assertTrue(state.active)
    }

    @Test
    fun `active can be set to false`() {
        val state = RocketState(x = 100f, y = 100f)
        state.active = false
        assertFalse(state.active)
    }

    @Test
    fun `position can be updated`() {
        val state = RocketState(x = 100f, y = 100f)
        state.x = 200f
        state.y = 50f
        assertEquals(200f, state.x, 0.01f)
        assertEquals(50f, state.y, 0.01f)
    }
}
