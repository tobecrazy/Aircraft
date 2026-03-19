package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class AircraftDataTest {

    @Test
    fun `isAlive returns true when healthPoints is positive`() {
        val aircraft = Aircraft(name = "Test", health_points = 50f)
        assertTrue(aircraft.isAlive())
    }

    @Test
    fun `isAlive returns false when healthPoints is zero`() {
        val aircraft = Aircraft(name = "Test", health_points = 0f)
        assertFalse(aircraft.isAlive())
    }

    @Test
    fun `isAlive returns false when healthPoints is negative`() {
        val aircraft = Aircraft(name = "Test", health_points = -10f)
        assertFalse(aircraft.isAlive())
    }

    @Test
    fun `hit reduces healthPoints by BULLET_DAMAGE`() {
        val aircraft = Aircraft(name = "Test", health_points = 100f)
        aircraft.hit()
        assertEquals(100f - Aircraft.BULLET_DAMAGE, aircraft.health_points, 0.01f)
    }

    @Test
    fun `restoreHealth sets healthPoints to MAX_HP`() {
        val aircraft = Aircraft(name = "Test", health_points = 20f)
        aircraft.restoreHealth()
        assertEquals(Aircraft.MAX_HP, aircraft.health_points, 0.01f)
    }

    @Test
    fun `isFullHealth returns true when healthPoints equals MAX_HP`() {
        val aircraft = Aircraft(name = "Test", health_points = Aircraft.MAX_HP)
        assertTrue(aircraft.isFullHealth())
    }

    @Test
    fun `isFullHealth returns true when healthPoints is greater than MAX_HP`() {
        val aircraft = Aircraft(name = "Test", health_points = Aircraft.MAX_HP + 10f)
        assertTrue(aircraft.isFullHealth())
    }

    @Test
    fun `isFullHealth returns false when healthPoints is less than MAX_HP`() {
        val aircraft = Aircraft(name = "Test", health_points = Aircraft.MAX_HP - 1f)
        assertFalse(aircraft.isFullHealth())
    }

    @Test
    fun `multiple hits reduce health incrementally`() {
        val aircraft = Aircraft(name = "Test", health_points = 100f)
        aircraft.hit()
        aircraft.hit()
        aircraft.hit()
        aircraft.hit()
        aircraft.hit()
        // 100 - 5 * 20 = 0
        assertEquals(0f, aircraft.health_points, 0.01f)
        assertFalse(aircraft.isAlive())
    }

    @Test
    fun `default healthPoints is MAX_HP`() {
        val aircraft = Aircraft(name = "Test")
        assertEquals(Aircraft.MAX_HP, aircraft.health_points, 0.01f)
    }

    @Test
    fun `default lethality is 20`() {
        val aircraft = Aircraft(name = "Test")
        assertEquals(20.0f, aircraft.lethality, 0.01f)
    }

    @Test
    fun `default icon is zero`() {
        val aircraft = Aircraft(name = "Test")
        assertEquals(0, aircraft.icon)
    }
}
