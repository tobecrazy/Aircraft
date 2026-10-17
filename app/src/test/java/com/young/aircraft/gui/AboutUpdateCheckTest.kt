package com.young.aircraft.gui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the About screen's manual version-check decision. */
class AboutUpdateCheckTest {

    @Test
    fun `current equals latest is already up to date`() {
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "1.4.1",
                latestVersion = "1.4.8"
            )
        )
    }

    @Test
    fun `current newer than latest is already up to date`() {
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.9",
                minimumVersion = "1.4.1",
                latestVersion = "1.4.8"
            )
        )
    }

    @Test
    fun `older than latest offers the optional update`() {
        assertEquals(
            AboutUpdateCheckResult.UpdateAvailable("1.4.9"),
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "1.4.1",
                latestVersion = "1.4.9"
            )
        )
    }

    @Test
    fun `blank or malformed latest fails open to already latest`() {
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "1.4.1",
                latestVersion = ""
            )
        )
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "1.4.1",
                latestVersion = "1.x"
            )
        )
    }

    @Test
    fun `below minimum forces update before any optional prompt`() {
        assertEquals(
            AboutUpdateCheckResult.ForcedUpdate,
            resolveAboutUpdateCheck(
                currentVersion = "1.3.9",
                minimumVersion = "1.4.1",
                latestVersion = "1.4.2"
            )
        )
    }

    @Test
    fun `malformed or blank minimum matches app pipeline and stays already latest`() {
        // The app-wide pipeline (RepoUpdateConfigStore.isOptionalUpdateAvailable)
        // suppresses every optional prompt when the minimum cannot be parsed;
        // the manual check must behave the same.
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "",
                latestVersion = "1.4.9"
            )
        )
        assertEquals(
            AboutUpdateCheckResult.AlreadyLatest,
            resolveAboutUpdateCheck(
                currentVersion = "1.4.8",
                minimumVersion = "1.x",
                latestVersion = "1.4.9"
            )
        )
    }
}
