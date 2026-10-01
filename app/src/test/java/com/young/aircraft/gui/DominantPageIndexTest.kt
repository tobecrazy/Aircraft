package com.young.aircraft.gui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the page counter reaching "N / N". `firstVisibleItemIndex` cannot do this: it is the item
 * at the viewport top, and the last page never reaches the top once the list is scrolled to the end.
 */
class DominantPageIndexTest {

    /** 8 pages of 2000px in a 1000px viewport, laid out from `firstTop`. */
    private fun pages(count: Int, size: Int, firstTop: Int) =
        (0 until count).map { PageLayout(it, firstTop + it * size, size) }

    private val viewport = 1000

    @Test
    fun `top of the list reports the first page`() {
        assertEquals(0, dominantPageIndex(0, viewport, pages(8, 2000, 0)))
    }

    @Test
    fun `scrolled to the end reports the last page`() {
        // Max scroll for 8x2000 in a 1000 viewport: last page top is 14000, bottom 16000.
        assertEquals(7, dominantPageIndex(14000, 15000, pages(8, 2000, 0)))
    }

    @Test
    fun `page holding the viewport centre wins over the topmost visible page`() {
        // Centre 5500 falls inside page 2 (4000..6000) while page 1 still occupies the top.
        assertEquals(2, dominantPageIndex(5000, 6000, pages(8, 2000, 0)))
    }

    @Test
    fun `short trailing page still reports the last page at full scroll`() {
        // Pages 0..6 are 2000px, page 7 is a 100px blank back page: 14000..14100.
        // At max scroll (13100..14100) the viewport centre lands in page 6, but the list cannot
        // scroll further, so the reader is on page 7.
        val laidOut = pages(7, 2000, 0) + PageLayout(7, 14000, 100)
        assertEquals(7, dominantPageIndex(13100, 14100, laidOut))
    }

    @Test
    fun `short trailing page does not hijack the page before it`() {
        // Same layout, but scrolled to where page 6 is centred and page 7 is still off-screen.
        val laidOut = pages(7, 2000, 0) + PageLayout(7, 14000, 100)
        assertEquals(6, dominantPageIndex(12000, 13000, laidOut))
    }

    @Test
    fun `empty layout falls back to the first page`() {
        assertEquals(0, dominantPageIndex(0, viewport, emptyList()))
    }

    @Test
    fun `viewport past every page falls back to the last one`() {
        // Defensive: stale layout must not read as page 0.
        assertEquals(7, dominantPageIndex(99_000, 100_000, pages(8, 2000, 0)))
    }
}