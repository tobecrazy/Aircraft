package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class BossStateTest {

    @Test
    fun `isDestroyed returns false when hitPoints is greater than zero`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = 500f, maxHitPoints = 1000f, bitmapIndex = 0)
        assertFalse(state.isDestroyed())
    }

    @Test
    fun `isDestroyed returns true when hitPoints is zero`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = 0f, maxHitPoints = 1000f, bitmapIndex = 0)
        assertTrue(state.isDestroyed())
    }

    @Test
    fun `isDestroyed returns true when hitPoints is negative`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = -10f, maxHitPoints = 1000f, bitmapIndex = 0)
        assertTrue(state.isDestroyed())
    }

    @Test
    fun `isExpired returns false when destroyedTime is zero`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = 0f, maxHitPoints = 1000f, bitmapIndex = 0, destroyedTime = 0L)
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns false when destroyedTime is recent`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = 0f, maxHitPoints = 1000f, bitmapIndex = 0, destroyedTime = System.currentTimeMillis())
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns true when destroyedTime is older than 3500ms`() {
        val oldTime = System.currentTimeMillis() - 4000L
        val state = BossState(x = 100f, y = 100f, hitPoints = 0f, maxHitPoints = 1000f, bitmapIndex = 0, destroyedTime = oldTime)
        assertTrue(state.isExpired())
    }

    @Test
    fun `bomb can be added to bombs list`() {
        val state = BossState(x = 100f, y = 100f, hitPoints = 100f, maxHitPoints = 1000f, bitmapIndex = 0)
        state.bombs.add(BossBomb(x = 50f, y = 50f, bitmapIndex = 1))
        assertEquals(1, state.bombs.size)
    }
}
