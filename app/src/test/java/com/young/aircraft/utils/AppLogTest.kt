package com.young.aircraft.utils

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException

// Plain Application: AircraftApplication seeds AppLog from DataStore on a background
// coroutine, which would flip the switch mid-assertion.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLogTest {

    private val tag = "AppLogTestTag"

    @Before
    fun setUp() {
        ShadowLog.clear()
        AppLog.enabled = false
    }

    @After
    fun tearDown() {
        AppLog.enabled = false
    }

    @Test
    fun `disabled switch silences every level`() {
        AppLog.i(tag, "info")
        AppLog.d(tag) { "debug" }
        AppLog.w(tag, "warn")
        AppLog.e(tag, "error", IOException("boom"))
        AppLog.v(tag, "verbose")

        assertTrue(ShadowLog.getLogsForTag(tag).isEmpty())
    }

    @Test
    fun `enabled switch forwards every level with tag and message`() {
        AppLog.enabled = true

        AppLog.i(tag, "info")
        AppLog.d(tag) { "debug" }
        AppLog.w(tag, "warn")
        AppLog.e(tag, "error", IOException("boom"))
        AppLog.v(tag, "verbose")

        val logged = ShadowLog.getLogsForTag(tag)
        assertEquals(listOf("info", "debug", "warn", "error", "verbose"), logged.map { it.msg })
        assertEquals(
            listOf(android.util.Log.INFO, android.util.Log.DEBUG, android.util.Log.WARN, android.util.Log.ERROR, android.util.Log.VERBOSE),
            logged.map { it.type }
        )
        assertEquals("boom", logged.single { it.type == android.util.Log.ERROR }.throwable?.message)
    }

    @Test
    fun `debug message is not built while disabled`() {
        var built = 0

        AppLog.d(tag) { built++; "debug" }

        assertEquals(0, built)
        AppLog.enabled = true
        AppLog.d(tag) { built++; "debug" }
        assertEquals(1, built)
    }
}