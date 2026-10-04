package com.young.aircraft.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.utils.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain Application: AircraftApplication keeps its own LogSettings instance and collector
// alive for the whole test, which double-reads the same DataStore file.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LogSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.resolve("datastore").deleteRecursively()
        AppLog.enabled = false
        LogSettings.defaultEnabled = false
    }

    @After
    fun tearDown() {
        LogSettings.defaultEnabled = false
        AppLog.enabled = false
    }

    @Test
    fun `setEnabled persists and flips the runtime switch on the same call`() = runBlocking {
        val settings = LogSettings(context)

        settings.setEnabled(true)

        assertTrue("switch must land before the write lands", AppLog.enabled)
        assertTrue(settings.enabledFlow.first())

        settings.setEnabled(false)

        assertFalse(AppLog.enabled)
        assertFalse(settings.enabledFlow.first())
    }

    @Test
    fun `stored preference wins over a later default change`() = runBlocking {
        val settings = LogSettings(context)
        settings.setEnabled(true)

        LogSettings.defaultEnabled = false

        assertTrue(settings.enabledFlow.first())
    }
}