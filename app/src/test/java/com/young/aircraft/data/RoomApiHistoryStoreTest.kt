package com.young.aircraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.providers.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the :app Room implementation of the dev-tools history contract. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomApiHistoryStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var store: RoomApiHistoryStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(db)
        store = RoomApiHistoryStore(context)
    }

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.setDatabase(null)
    }

    @Test
    fun `insert then observe round-trips every column`() = runTest {
        store.insert(
            com.young.developtools.repository.ApiHistoryRecord(
                url = "https://example.com/api",
                method = "POST",
                requestHeaders = "{\"A\":\"1\"}",
                requestBody = "{\"k\":2}",
                responseCode = 201,
                responseBody = "{}",
                responseHeaders = "{\"X\":[\"y\"]}",
                responseTime = 12L,
                error = null
            )
        )

        val rows = store.observeAll().first()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("https://example.com/api", row.url)
        assertEquals("POST", row.method)
        assertEquals("{\"A\":\"1\"}", row.requestHeaders)
        assertEquals("{\"k\":2}", row.requestBody)
        assertEquals(201, row.responseCode)
        assertEquals("{}", row.responseBody)
        assertEquals("{\"X\":[\"y\"]}", row.responseHeaders)
        assertEquals(12L, row.responseTime)
        assertNull(row.error)
        assertTrue(row.id != 0L)
        assertTrue(row.timestamp > 0L)
    }

    @Test
    fun `getById deleteById and clear behave`() = runTest {
        store.insert(
            com.young.developtools.repository.ApiHistoryRecord(
                url = "https://example.com/1",
                method = "GET",
                requestHeaders = "{}",
                requestBody = null,
                responseCode = null,
                responseBody = null,
                responseHeaders = null,
                responseTime = null,
                error = "boom"
            )
        )
        val id = store.observeAll().first().first().id

        assertEquals("boom", store.getById(id)?.error)
        store.deleteById(id)
        assertNull(store.getById(id))

        store.insert(
            com.young.developtools.repository.ApiHistoryRecord(
                url = "https://example.com/2",
                method = "GET",
                requestHeaders = "{}",
                requestBody = null,
                responseCode = null,
                responseBody = null,
                responseHeaders = null,
                responseTime = null,
                error = null
            )
        )
        store.clear()
        assertTrue(store.observeAll().first().isEmpty())
    }
}
