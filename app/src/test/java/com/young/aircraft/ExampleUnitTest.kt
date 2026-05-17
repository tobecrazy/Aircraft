package com.young.aircraft

import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.gui.createSolvedTiles
import com.young.aircraft.gui.formatTime
import com.young.aircraft.gui.gridSizeForDifficulty
import com.young.aircraft.gui.isSolved
import com.young.aircraft.gui.moveTile
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `grid size follows difficulty coefficients`() {
        assertEquals(2, gridSizeForDifficulty(GameDifficulty.EASY))
        assertEquals(3, gridSizeForDifficulty(GameDifficulty.NORMAL))
        assertEquals(4, gridSizeForDifficulty(GameDifficulty.HARD))
    }

    @Test
    fun `move tile slides only adjacent tile into blank`() {
        val tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8)
        val moved = moveTile(tiles, tileValue = 8, gridSize = 3)
        assertTrue(moved.moved)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 0), moved.tiles)
    }

    @Test
    fun `solved board detection and time formatting`() {
        assertTrue(isSolved(createSolvedTiles(3)))
        assertEquals("02:05", formatTime(125))
    }
}
