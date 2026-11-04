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
            game.parkBeforeClose()
        }
    }

    @Test
    fun pauseActionShowsPausePanel() {
        withScreen(MainActivity::class.java) { game ->
            assertTrue("pause action", game.waitForText(R.string.game_hud_pause))
            assertTrue("pause action must be clickable", game.clickText(R.string.game_hud_pause))

            assertTrue("pause panel badge", game.waitForText(R.string.game_hud_pause_badge))
            assertTrue("resume action", game.waitForText(R.string.pause_resume))
            game.parkBeforeClose()
        }
    }

    @Test
    fun resumeActionHidesPausePanel() {
        withScreen(MainActivity::class.java) { game ->
            assertTrue("pause action", game.waitForText(R.string.game_hud_pause))
            assertTrue("pause action must be clickable", game.clickText(R.string.game_hud_pause))
            assertTrue("pause panel badge", game.waitForText(R.string.game_hud_pause_badge))
            // Let the panel's fade-in settle before resuming. Invoking the click
            // while the enter transition is still running overlaps enter/exit
            // transitions, and on the SwiftShader CI emulator (single frames up
            // to ~4s) the interrupted exit can keep the badge node in the
            // semantics tree far longer than the default window.
            Thread.sleep(500)

            assertTrue("resume action must be clickable", game.clickText(R.string.pause_resume))
            // Resuming must tear the panel down again, not just flip a label.
            // The badge leaves the semantics tree only after the exit animation
            // finishes, so the wait must tolerate starved Compose frames.
            assertTrue("resume hides the panel",
                game.waitForNoText(R.string.game_hud_pause_badge, timeoutMs = 30_000))
            game.parkBeforeClose()
        }
    }

    /**
     * Parks the game before the window is torn down. The emulator's software EGL
     * driver can crash while destroying a surface that still has a frame in
     * flight, and a process crash aborts the whole instrumentation run; a paused
     * loop leaves nothing to tear down.
     */
    private fun MainActivity.parkBeforeClose() {
        clickText(R.string.game_hud_pause)
        Thread.sleep(300)
    }

    @Test
    fun puzzleScreenShowsTopBar() {
        withScreen(PuzzleActivity::class.java) { puzzle ->
            assertTrue("puzzle title", puzzle.waitForText(R.string.puzzle_top_bar_title))
        }
    }
}
