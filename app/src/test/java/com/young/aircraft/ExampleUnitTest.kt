package com.young.aircraft

import com.young.aircraft.data.GameDifficulty
import androidx.compose.ui.geometry.Offset
import com.young.aircraft.gui.PuzzleMove
import com.young.aircraft.gui.PuzzlePieceState
import com.young.aircraft.gui.createPuzzlePieces
import com.young.aircraft.gui.dragPuzzlePiece
import com.young.aircraft.gui.formatTime
import com.young.aircraft.gui.gridSizeForDifficulty
import com.young.aircraft.gui.hasPieceMoved
import com.young.aircraft.gui.restorePuzzleMove
import com.young.aircraft.gui.snapPuzzlePiece
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
        assertEquals(3, gridSizeForDifficulty(GameDifficulty.EASY))
        assertEquals(4, gridSizeForDifficulty(GameDifficulty.NORMAL))
        assertEquals(5, gridSizeForDifficulty(GameDifficulty.HARD))
    }

    @Test
    fun `drag puzzle piece clamps inside board`() {
        val pieces = listOf(PuzzlePieceState(id = 1, row = 0, col = 0, x = 10f, y = 10f))

        val dragged = dragPuzzlePiece(
            pieces = pieces,
            pieceId = 1,
            delta = Offset(-100f, 500f),
            boardSizePx = 300f,
            gridSize = 3,
            playAreaHeightPx = 500f
        )

        assertEquals(0f, dragged.first().x, 0.01f)
        assertEquals(400f, dragged.first().y, 0.01f)
    }

    @Test
    fun `snap puzzle piece locks it to target when close`() {
        val pieces = listOf(PuzzlePieceState(id = 5, row = 1, col = 1, x = 135f, y = 132f))

        val result = snapPuzzlePiece(
            pieces = pieces,
            pieceId = 5,
            gridSize = 3,
            boardSizePx = 300f
        )

        assertTrue(result.snapped)
        assertTrue(result.pieces.first().snapped)
        assertEquals(100f, result.pieces.first().x, 0.01f)
        assertEquals(100f, result.pieces.first().y, 0.01f)
    }

    @Test
    fun `snap puzzle piece ignores far drops`() {
        val pieces = listOf(PuzzlePieceState(id = 5, row = 1, col = 1, x = 160f, y = 160f))

        val result = snapPuzzlePiece(
            pieces = pieces,
            pieceId = 5,
            gridSize = 3,
            boardSizePx = 300f
        )

        assertFalse(result.snapped)
        assertFalse(result.pieces.first().snapped)
        assertEquals(160f, result.pieces.first().x, 0.01f)
        assertEquals(160f, result.pieces.first().y, 0.01f)
    }

    @Test
    fun `restore puzzle move returns piece to previous position`() {
        val previous = PuzzlePieceState(id = 2, row = 0, col = 1, x = 48f, y = 12f)
        val current = previous.copy(x = 100f, y = 0f, snapped = true)

        val restored = restorePuzzleMove(listOf(current), PuzzleMove(pieceId = 2, previous = previous))

        assertEquals(previous, restored.first())
        assertTrue(hasPieceMoved(previous, current))
    }

    @Test
    fun `piece creation and time formatting are stable`() {
        val pieces = createPuzzlePieces(gridSize = 3, boardSizePx = 300f, level = 1, playAreaHeightPx = 500f)

        assertEquals(9, pieces.size)
        assertEquals((1..9).toList(), pieces.map { it.id })
        assertTrue(pieces.all { it.y >= 300f })
        assertEquals("02:05", formatTime(125))
    }
}
