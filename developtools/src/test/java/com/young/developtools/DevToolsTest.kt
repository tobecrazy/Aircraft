package com.young.developtools

import androidx.test.core.app.ApplicationProvider
import com.young.developtools.repository.InMemoryApiHistoryStore
import com.young.developtools.utils.DevLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the host bridge defaults: every hook is safe when the app wires nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevToolsTest {

    @Before
    fun setUp() {
        DevTools.openActivityMonitor = null
        DevTools.onInvincibleChanged = null
        DevTools.historyStoreProvider = null
        DevTools.onLogEnabledChanged = null
        DevLog.enabled = false
    }

    @After
    fun tearDown() {
        DevTools.openActivityMonitor = null
        DevTools.onInvincibleChanged = null
        DevTools.historyStoreProvider = null
        DevTools.onLogEnabledChanged = null
        DevLog.enabled = false
    }

    @Test
    fun `resolveHistoryStore falls back to memory and round-trips`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DevTools.resolveHistoryStore(context)
        assertTrue(store is InMemoryApiHistoryStore)

        store.insert(
            com.young.developtools.repository.ApiHistoryRecord(
                url = "https://example.com",
                method = "GET",
                requestHeaders = "{}",
                requestBody = null,
                responseCode = 200,
                responseBody = "ok",
                responseHeaders = null,
                responseTime = 1L,
                error = null
            )
        )
        val rows = store.observeAll().first()
        assertEquals(1, rows.size)
        val id = rows.first().id
        assertNotNull(store.getById(id))
        store.deleteById(id)
        assertNull(store.getById(id))
        store.clear()
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test
    fun `setEnabledAndForward fans out to the host hook when wired`() {
        var forwarded: Boolean? = null
        DevTools.onLogEnabledChanged = { forwarded = it }

        DevLog.setEnabledAndForward(true)
        assertTrue(DevLog.enabled)
        assertEquals(true, forwarded)

        DevTools.onLogEnabledChanged = null
        DevLog.setEnabledAndForward(false)
        assertEquals(false, DevLog.enabled)
        assertEquals(true, forwarded)
    }

    @Test
    fun `hostVersionName reads the runtime package`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val version = DevTools.hostVersionName(context)
        assertNotNull(version)
        assertTrue(version.isNotBlank())
    }
}
