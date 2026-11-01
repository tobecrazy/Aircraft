package com.young.aircraft.gui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.young.aircraft.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the two real-time game surfaces. Both render
 * mostly through non-Compose views (GameCoreView canvas, puzzle board), so the
 * text assertions target their HUD chrome and the View tree covers the canvas.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CombatAndPuzzleScreensTest {

    @Test
    fun combatScreenShowsHudAndGameCoreView() {
        withScreen(MainActivity::class.java) { game ->
            // The HUD overlay (Compose) and the canvas (GameCoreView) must both
            // be live — that coexistence is deliberate in MainActivity.
            assertTrue("pause action", game.waitForText(R.string.game_hud_pause))
            assertTrue("GameCoreView must be attached to the window", game.hasGameCoreView())
        }
    }

    @Test
    fun puzzleScreenShowsTopBar() {
        withScreen(PuzzleActivity::class.java) { puzzle ->
            assertTrue("puzzle title", puzzle.waitForText(R.string.puzzle_top_bar_title))
        }
    }
}
