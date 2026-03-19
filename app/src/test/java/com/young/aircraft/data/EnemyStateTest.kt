package com.young.aircraft.data

import org.junit.Assert.*
import org.junit.Test

class EnemyStateTest {

    @Test
    fun `isDestroyed returns false when health is positive`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 1f)
        assertFalse(state.isDestroyed())
    }

    @Test
    fun `isDestroyed returns true when health is zero`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 0f)
        assertTrue(state.isDestroyed())
    }

    @Test
    fun `isDestroyed returns true when health is negative`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = -5f)
        assertTrue(state.isDestroyed())
    }

    @Test
    fun `isExpired returns false when destroyedTime is zero`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 0f, destroyedTime = 0L)
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns false when destroyedTime is recent`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 0f, destroyedTime = System.currentTimeMillis())
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired returns true when destroyedTime is older than 1000ms`() {
        val oldTime = System.currentTimeMillis() - 1500L
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 0f, destroyedTime = oldTime)
        assertTrue(state.isExpired())
    }

    @Test
    fun `bullets list can be modified`() {
        val state = EnemyState(x = 100f, y = 100f, bitmap = null, health = 1f)
        state.bullets.add(EnemyBullet(y = 200f, originY = 100f))
        assertEquals(1, state.bullets.size)
        assertEquals(200f, state.bullets[0].y, 0.01f)
    }
}
