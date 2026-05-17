package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class AircraftDataTest {

    @Test
    fun `extractPuzzleImageUrlsFromPeapixFeed returns ordered image urls`() {
        val feedJson = """
            [
              {"imageUrl":"https://img.peapix.com/a.jpg","date":"2026-05-15"},
              {"imageUrl":"https://img.peapix.com/b.jpg","date":"2026-05-14"},
              {"imageUrl":"https://img.peapix.com/c.jpg","date":"2026-05-13"}
            ]
        """.trimIndent()

        val urls = AircraftConstants.Urls.extractPuzzleImageUrlsFromPeapixFeed(feedJson)

        assertEquals(
            listOf(
                "https://img.peapix.com/a.jpg",
                "https://img.peapix.com/b.jpg",
                "https://img.peapix.com/c.jpg"
            ),
            urls
        )
    }

    @Test
    fun `extractLatestPuzzleImageUrlFromPeapixFeed returns first image url`() {
        val feedJson = """
            [
              {"imageUrl":"https://img.peapix.com/latest.jpg","date":"2026-05-15"},
              {"imageUrl":"https://img.peapix.com/older.jpg","date":"2026-05-14"}
            ]
        """.trimIndent()

        val latestUrl = AircraftConstants.Urls.extractLatestPuzzleImageUrlFromPeapixFeed(feedJson)

        assertEquals("https://img.peapix.com/latest.jpg", latestUrl)
    }

    @Test
    fun `extractPuzzleImageUrlsFromPeapixFeed returns empty list on invalid json`() {
        val urls = AircraftConstants.Urls.extractPuzzleImageUrlsFromPeapixFeed("invalid")
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `isAlive returns true when healthPoints is positive`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = 50f)
        assertTrue(aircraft.isAlive())
    }

    @Test
    fun `isAlive returns false when healthPoints is zero`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = 0f)
        assertFalse(aircraft.isAlive())
    }

    @Test
    fun `isAlive returns false when healthPoints is negative`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = -10f)
        assertFalse(aircraft.isAlive())
    }

    @Test
    fun `hit reduces healthPoints by BULLET_DAMAGE`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = 100f)
        aircraft.hit()
        assertEquals(100f - PlayerAircraft.BULLET_DAMAGE, aircraft.health_points, 0.01f)
    }

    @Test
    fun `restoreHealth sets healthPoints to MAX_HP`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = 20f)
        aircraft.restoreHealth()
        assertEquals(PlayerAircraft.MAX_HP, aircraft.health_points, 0.01f)
    }

    @Test
    fun `isFullHealth returns true when healthPoints equals MAX_HP`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = PlayerAircraft.MAX_HP)
        assertTrue(aircraft.isFullHealth())
    }

    @Test
    fun `isFullHealth returns true when healthPoints is greater than MAX_HP`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = PlayerAircraft.MAX_HP + 10f)
        assertTrue(aircraft.isFullHealth())
    }

    @Test
    fun `isFullHealth returns false when healthPoints is less than MAX_HP`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = PlayerAircraft.MAX_HP - 1f)
        assertFalse(aircraft.isFullHealth())
    }

    @Test
    fun `multiple hits reduce health incrementally`() {
        val aircraft = PlayerAircraft(name = "Test", health_points = 100f)
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
    fun `hit does nothing when invincible mode is enabled in GameStateManager`() {
        com.young.aircraft.common.GameStateManager.isInvincible = true
        try {
            val aircraft = PlayerAircraft(name = "Test", health_points = 100f)
            aircraft.hit()
            assertEquals(100f, aircraft.health_points, 0.01f)
            
            aircraft.hit()
            assertEquals(100f, aircraft.health_points, 0.01f)
        } finally {
            com.young.aircraft.common.GameStateManager.isInvincible = false
        }
    }

    @Test
    fun `default healthPoints is MAX_HP`() {
        val aircraft = PlayerAircraft(name = "Test")
        assertEquals(PlayerAircraft.MAX_HP, aircraft.health_points, 0.01f)
    }

    @Test
    fun `default lethality is 20`() {
        val aircraft = PlayerAircraft(name = "Test")
        assertEquals(20.0f, aircraft.lethality, 0.01f)
    }

    @Test
    fun `default icon is zero`() {
        val aircraft = PlayerAircraft(name = "Test")
        assertEquals(0, aircraft.icon)
    }
}
