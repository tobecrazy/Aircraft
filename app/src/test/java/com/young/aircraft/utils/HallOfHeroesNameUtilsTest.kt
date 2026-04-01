package com.young.aircraft.utils

import com.young.aircraft.data.PlayerGameData
import org.junit.Assert.assertEquals
import org.junit.Test

class HallOfHeroesNameUtilsTest {

    @Test
    fun `resolve submitted name falls back to anonymous when blank`() {
        val resolvedName = HallOfHeroesNameUtils.resolveSubmittedName("   ", "Anonymous")

        assertEquals("Anonymous", resolvedName)
    }

    @Test
    fun `resolve submitted name trims player input`() {
        val resolvedName = HallOfHeroesNameUtils.resolveSubmittedName("  Ace Pilot  ", "Anonymous")

        assertEquals("Ace Pilot", resolvedName)
    }

    @Test
    fun `display name prefers stored player name`() {
        val record = PlayerGameData(
            playerId = "player-123456",
            playerName = "Captain Nova",
            level = 10,
            score = 12_000L
        )

        assertEquals("Captain Nova", HallOfHeroesNameUtils.getDisplayName(record))
    }

    @Test
    fun `display name falls back to truncated player id`() {
        val record = PlayerGameData(
            playerId = "player-123456",
            level = 3,
            score = 2_000L
        )

        assertEquals("player\u2026", HallOfHeroesNameUtils.getDisplayName(record))
    }
}
