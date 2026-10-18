package com.young.aircraft.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure transitions for the puzzle session state: moves, scan use, retry penalty,
 * round result, and the next-playable-level save target.
 */
class PuzzleSessionReducerTest {

    private val gridSize = 3
    private val levelDurationSeconds = 120

    private fun reduce(
        state: PuzzleSessionState,
        event: PuzzleSessionEvent
    ): PuzzleSessionState = reducePuzzleSession(state, event, gridSize, levelDurationSeconds)

    private fun startedRound(): PuzzleSessionState = createInitialPuzzleSession(1, 0L, gridSize)

    @Test
    fun `initial session derives reset token from level and grid`() {
        val session = createInitialPuzzleSession(3, 250L, gridSize)
        assertEquals(3, session.level)
        assertEquals(250L, session.totalScore)
        assertEquals(PUZZLE_SCANS_PER_LEVEL, session.scansRemaining)
        assertEquals(0, session.moves)
        assertEquals(3 * 100 + gridSize, session.boardResetToken)
    }

    @Test
    fun `piece dropped counts one move`() {
        val after = reduce(startedRound(), PuzzleSessionEvent.PieceDropped)
        assertEquals(1, after.moves)
    }

    @Test
    fun `placed count changes update progress only during the round`() {
        val progress = reduce(startedRound(), PuzzleSessionEvent.PlacedCountChanged(4))
        assertEquals(4, progress.placedCount)

        val solved = reduce(progress, PuzzleSessionEvent.Solved)
        assertSame(solved, reduce(solved, PuzzleSessionEvent.PlacedCountChanged(99)))
    }

    @Test
    fun `solved round applies the result exactly once and points the save to the next level`() {
        val inProgress = startedRound().copy(
            moves = 9,
            elapsedSeconds = 30,
            scansRemaining = 2,
            retries = 1
        )

        val solved = reduce(inProgress, PuzzleSessionEvent.Solved)
        val expected = calculatePuzzleRoundResult(1, gridSize, moves = 9, scansUsed = 1, retries = 1, remainingSeconds = 90)
        assertTrue(solved.roundSolved)
        assertEquals(expected.score, solved.roundScore)
        assertEquals(expected.score, solved.totalScore)
        assertEquals(expected.stars, solved.roundStars)
        assertEquals(1, solved.scansUsed)
        assertEquals(2, solved.saveTargetLevel)

        // A second solve event must not add another round result.
        assertSame(solved, reduce(solved, PuzzleSessionEvent.Solved))
    }

    @Test
    fun `scan requests consume scans and are refused when none remain`() {
        val afterFirst = reduce(startedRound(), PuzzleSessionEvent.ScanRequested)
        assertEquals(PUZZLE_SCANS_PER_LEVEL - 1, afterFirst.scansRemaining)

        val afterSecond = reduce(afterFirst, PuzzleSessionEvent.ScanRequested)
        assertEquals(1, afterSecond.scansRemaining)
        val afterThird = reduce(afterSecond, PuzzleSessionEvent.ScanRequested)
        assertEquals(0, afterThird.scansRemaining)

        assertSame(afterThird, reduce(afterThird, PuzzleSessionEvent.ScanRequested))
    }

    @Test
    fun `retry only applies after timeout and resets the round with a penalty`() {
        val notTimedOut = startedRound()
        assertSame(notTimedOut, reduce(notTimedOut, PuzzleSessionEvent.RetryRequested))

        val timedOut = notTimedOut.copy(elapsedSeconds = 100, roundTimedOut = true, boardResetToken = 7)
        val retried = reduce(timedOut, PuzzleSessionEvent.RetryRequested)
        assertEquals(1, retried.retries)
        assertEquals(0, retried.elapsedSeconds)
        assertEquals(0, retried.placedCount)
        assertFalse(retried.roundTimedOut)
        assertEquals(8, retried.boardResetToken)
        assertEquals(1, retried.level)
    }

    @Test
    fun `continue advances to the next playable level and resets the round`() {
        val solved = reduce(
            startedRound().copy(moves = 8, scansRemaining = 1, retries = 0),
            PuzzleSessionEvent.Solved
        )

        val continued = reduce(solved, PuzzleSessionEvent.ContinueRequested)
        assertEquals(2, continued.level)
        assertEquals(0, continued.moves)
        assertEquals(0, continued.elapsedSeconds)
        assertEquals(PUZZLE_SCANS_PER_LEVEL, continued.scansRemaining)
        assertEquals(0, continued.retries)
        assertEquals(0, continued.placedCount)
        assertEquals(0L, continued.roundScore)
        assertEquals(0, continued.roundStars)
        assertFalse(continued.roundSolved)
        assertFalse(continued.roundTimedOut)
        assertEquals(solved.boardResetToken + 1, continued.boardResetToken)
        // The earned round score is already in the cumulative score and survives the switch.
        assertEquals(solved.roundScore, continued.totalScore)
    }

    @Test
    fun `reopened session at the saved level cannot rescore the prior round`() {
        val wonRound = reduce(
            startedRound().copy(moves = 9, elapsedSeconds = 30),
            PuzzleSessionEvent.Solved
        )
        val savedScore = wonRound.totalScore

        // The activity start values for a reopen: next playable level + accumulated score.
        val reopened = createInitialPuzzleSession(wonRound.saveTargetLevel, savedScore, gridSize)
        assertEquals(2, reopened.level)
        assertEquals(savedScore, reopened.totalScore)
        assertFalse("A reopened session must not carry a pre-applied round result", reopened.roundSolved)
        assertEquals(0L, reopened.roundScore)

        // Only the new, played round can produce a round score once more.
        val playedAgain = reduce(
            reduce(reopened.copy(moves = 9), PuzzleSessionEvent.Solved),
            PuzzleSessionEvent.Solved
        )
        val expectedAgain = calculatePuzzleRoundResult(2, gridSize, moves = 9, scansUsed = 0, retries = 0, remainingSeconds = levelDurationSeconds)
        assertTrue(playedAgain.roundSolved)
        assertEquals(expectedAgain.score, playedAgain.roundScore)
        assertEquals(savedScore + expectedAgain.score, playedAgain.totalScore)
        assertEquals(3, playedAgain.saveTargetLevel)
    }

    @Test
    fun `final level continues nowhere and the finale save keeps level ten`() {
        val finalLevel = createInitialPuzzleSession(PUZZLE_LEVEL_COUNT, 900L, gridSize)
        val solved = reduce(finalLevel, PuzzleSessionEvent.Solved)
        assertTrue(solved.roundSolved)
        assertEquals(PUZZLE_LEVEL_COUNT, solved.saveTargetLevel)
        assertSame(solved, reduce(solved, PuzzleSessionEvent.ContinueRequested))
    }

    @Test
    fun `expiry happens once and a solved round ignores expiry`() {
        val expired = reduce(startedRound(), PuzzleSessionEvent.Expired)
        assertTrue(expired.roundTimedOut)
        assertSame(expired, reduce(expired, PuzzleSessionEvent.Expired))

        // A solved round must never flip into the timeout state.
        val solved = reduce(startedRound(), PuzzleSessionEvent.Solved)
        assertSame(solved, reduce(solved, PuzzleSessionEvent.Expired))
        assertFalse(solved.roundTimedOut)
    }

    @Test
    fun `in progress and timed out rounds save the current level`() {
        assertEquals(1, startedRound().saveTargetLevel)
        assertEquals(1, reduce(startedRound(), PuzzleSessionEvent.Expired).saveTargetLevel)
    }

    @Test
    fun `tick stops at the level duration and stops after the round ends`() {
        val advanced = reduce(startedRound(), PuzzleSessionEvent.Tick)
        assertEquals(1, advanced.elapsedSeconds)

        val atLimit = startedRound().copy(elapsedSeconds = levelDurationSeconds)
        assertSame(atLimit, reduce(atLimit, PuzzleSessionEvent.Tick))

        val solved = reduce(startedRound(), PuzzleSessionEvent.Solved)
        assertSame(solved, reduce(solved, PuzzleSessionEvent.Tick))
    }

    @Test
    fun `solve from an expired round never scores again`() {
        val expiredThenSolved = reduce(startedRound(), PuzzleSessionEvent.Expired)
        assertFalse(
            "The expired round must not also apply a solve result",
            reduce(expiredThenSolved, PuzzleSessionEvent.Solved).roundSolved
        )
    }

    @Test
    fun `score formula keeps level time move scan and retry terms`() {
        // 100 + level*25 + remaining*2 - over-par moves*5 - scans*100 - retries*150.
        assertEquals(
            115L,
            calculatePuzzleRoundResult(
                level = 3,
                gridSize = 4,
                moves = 20,
                scansUsed = 1,
                retries = 0,
                remainingSeconds = 30
            ).score
        )
        // Heavy penalties still land on the 50-point floor.
        assertEquals(
            50L,
            calculatePuzzleRoundResult(
                level = 1,
                gridSize = 3,
                moves = 50,
                scansUsed = 3,
                retries = 3,
                remainingSeconds = 0
            ).score
        )
        // A clean fast round earns three stars.
        val clean = calculatePuzzleRoundResult(
            level = 2,
            gridSize = 3,
            moves = 9,
            scansUsed = 0,
            retries = 0,
            remainingSeconds = 60
        )
        assertEquals(3, clean.stars)
        // Any retry drops the round to a single star.
        assertEquals(
            1,
            calculatePuzzleRoundResult(
                level = 1,
                gridSize = 3,
                moves = 9,
                scansUsed = 1,
                retries = 1,
                remainingSeconds = 60
            ).stars
        )
    }

    @Test
    fun `overlay states are deterministic and mutually exclusive`() {
        assertEquals(PuzzleOverlay.SCAN, puzzleOverlayFor(showGuide = true, scanActive = true, solved = true, timedOut = true))
        assertEquals(PuzzleOverlay.RESULT, puzzleOverlayFor(showGuide = true, scanActive = false, solved = true, timedOut = true))
        assertEquals(PuzzleOverlay.TIMEOUT, puzzleOverlayFor(showGuide = true, scanActive = false, solved = false, timedOut = true))
        assertEquals(PuzzleOverlay.GUIDE, puzzleOverlayFor(showGuide = true, scanActive = false, solved = false, timedOut = false))
        assertEquals(PuzzleOverlay.NONE, puzzleOverlayFor(showGuide = false, scanActive = false, solved = false, timedOut = false))
    }
}
