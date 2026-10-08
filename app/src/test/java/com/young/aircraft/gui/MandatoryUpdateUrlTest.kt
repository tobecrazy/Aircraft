package com.young.aircraft.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MandatoryUpdateUrlTest {

    @Test
    fun `builds tag url from plain version`() {
        assertEquals(
            "https://github.com/tobecrazy/Aircraft/releases/tag/V1.4.2",
            githubReleaseTagUrl("1.4.2")
        )
    }

    @Test
    fun `tolerates existing V prefix and surrounding whitespace`() {
        assertEquals(
            "https://github.com/tobecrazy/Aircraft/releases/tag/V1.4.2",
            githubReleaseTagUrl("V1.4.2")
        )
        assertEquals(
            "https://github.com/tobecrazy/Aircraft/releases/tag/V1.4.2",
            githubReleaseTagUrl("  1.4.2  ")
        )
    }

    @Test
    fun `returns null for blank version`() {
        assertNull(githubReleaseTagUrl(""))
        assertNull(githubReleaseTagUrl("   "))
    }
}
