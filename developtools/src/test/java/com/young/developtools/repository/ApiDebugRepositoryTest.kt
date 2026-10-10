package com.young.developtools.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiDebugRepositoryTest {

    private lateinit var store: ApiHistoryStore
    private lateinit var repository: ApiDebugRepository

    @Before
    fun setup() {
        store = mock()
        repository = ApiDebugRepository(store)
    }

    @Test
    fun `failed request is still saved to history with error`() = runTest {
        val result = repository.executeRequest(
            ApiDebugRequest(
                url = "ht!tp://::not-a-url",
                method = "GET",
                headers = emptyMap(),
                body = null
            )
        )

        assertTrue(result.isFailure)
        val captor = argumentCaptor<ApiHistoryRecord>()
        verify(store).insert(captor.capture())
        assertEquals("ht!tp://::not-a-url", captor.firstValue.url)
        assertEquals("GET", captor.firstValue.method)
        assertNotNull(captor.firstValue.error)
    }

    @Test
    fun `response headers survive a gson round trip`() {
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "Set-Cookie" to listOf("a=1", "b=2")
        )
        val stored = com.google.gson.Gson().toJson(headers)
        val parsed = repository.parseResponseHeaders(stored)
        assertEquals(headers, parsed)
    }

    @Test
    fun `parse helpers return empty maps for blank or corrupt input`() {
        assertTrue(repository.parseResponseHeaders(null).isEmpty())
        assertTrue(repository.parseResponseHeaders("not json").isEmpty())
        assertTrue(repository.parseRequestHeaders(null).isEmpty())
        assertTrue(repository.parseRequestHeaders("{broken").isEmpty())
    }

    @Test
    fun `request headers survive a gson round trip`() {
        val headers = mapOf("Authorization" to "Bearer token")
        val stored = com.google.gson.Gson().toJson(headers)
        assertEquals(headers, repository.parseRequestHeaders(stored))
    }
}
