package com.young.aircraft.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.ApiRequestHistory
import com.young.aircraft.providers.DatabaseProvider
import com.young.aircraft.repository.ApiDebugRequest
import com.young.aircraft.utils.apidebug.BodyError
import com.young.aircraft.utils.apidebug.HeaderErrorKind
import com.young.aircraft.utils.apidebug.UrlError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiDebugToolViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: ApiDebugToolViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        viewModel = ApiDebugToolViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        DatabaseProvider.setDatabase(null)
    }

    @Test
    fun `initial state should have default values`() = runTest {
        val state = viewModel.uiState.value
        assertEquals("", state.url)
        assertEquals("GET", state.method)
        assertEquals("Content-Type: application/json", state.headers)
        assertEquals("", state.requestBody)
        assertFalse(state.isLoading)
        assertEquals(null, state.response)
        assertEquals(null, state.error)
    }

    @Test
    fun `updateUrl should update state`() = runTest {
        val testUrl = "https://example.com/api"
        viewModel.updateUrl(testUrl)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(testUrl, viewModel.uiState.value.url)
    }

    @Test
    fun `updateMethod should update state`() = runTest {
        viewModel.updateMethod("POST")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("POST", viewModel.uiState.value.method)
    }

    @Test
    fun `updateHeaders should update state`() = runTest {
        val testHeaders = "Authorization: Bearer token\nContent-Type: application/json"
        viewModel.updateHeaders(testHeaders)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(testHeaders, viewModel.uiState.value.headers)
    }

    @Test
    fun `updateRequestBody should update state`() = runTest {
        val testBody = "{\"key\": \"value\"}"
        viewModel.updateRequestBody(testBody)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(testBody, viewModel.uiState.value.requestBody)
    }

    @Test
    fun `clearAll should reset state to defaults`() = runTest {
        viewModel.updateUrl("https://example.com")
        viewModel.updateMethod("POST")
        viewModel.updateHeaders("Custom: Header")
        viewModel.updateRequestBody("{\"test\": true}")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearAll()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.url)
        assertEquals("GET", state.method)
        assertEquals("Content-Type: application/json", state.headers)
        assertEquals("", state.requestBody)
    }

    @Test
    fun `initial state is invalid and cannot send`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(UrlError.EMPTY, state.urlError)
        assertFalse(state.canSend)
    }

    @Test
    fun `sendRequest with empty URL is blocked without network error`() = runTest {
        viewModel.sendRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(UrlError.EMPTY, state.urlError)
        assertEquals(null, state.response)
        // Blocked by the validation gate: no network attempt, no error toast state.
        assertEquals(null, state.error)
        assertFalse(state.canSend)
    }

    @Test
    fun `invalid headers block sending`() = runTest {
        viewModel.updateUrl("https://example.com/api")
        viewModel.updateHeaders("broken line without colon")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.headerErrors.size)
        assertEquals(HeaderErrorKind.NO_COLON, state.headerErrors.first().kind)
        assertFalse(state.canSend)

        viewModel.sendRequest()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.response)
    }

    @Test
    fun `invalid json body blocks sending`() = runTest {
        viewModel.updateUrl("https://example.com/api")
        viewModel.updateMethod("POST")
        viewModel.updateRequestBody("{not json")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BodyError.INVALID_JSON, state.bodyError)
        assertFalse(state.canSend)
    }

    @Test
    fun `body on GET is rejected`() = runTest {
        viewModel.updateUrl("https://example.com/api")
        viewModel.updateRequestBody("{\"key\": 1}")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(BodyError.NOT_ALLOWED, viewModel.uiState.value.bodyError)
        assertFalse(viewModel.uiState.value.canSend)
    }

    @Test
    fun `valid request can send`() = runTest {
        viewModel.updateUrl("https://example.com/api")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.urlError)
        assertTrue(state.headerErrors.isEmpty())
        assertEquals(null, state.bodyError)
        assertTrue(state.canSend)
    }

    @Test
    fun `importCurl fills all fields`() = runTest {
        viewModel.importCurl(
            com.young.aircraft.utils.apidebug.CurlRequest(
                url = "https://example.com/api",
                method = "POST",
                headers = listOf("Authorization" to "Bearer token"),
                body = "{\"a\":1}"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("https://example.com/api", state.url)
        assertEquals("POST", state.method)
        assertEquals("Authorization: Bearer token", state.headers)
        assertEquals("{\"a\":1}", state.requestBody)
        assertTrue(state.canSend)
    }

    @Test
    fun `loadHistoryById restores saved request`() = runTest {
        val dao = com.young.aircraft.providers.DatabaseProvider
            .getDatabase(context)
            .apiRequestHistoryDao()
        val id = dao.insert(
            ApiRequestHistory(
                url = "https://example.com/saved",
                method = "PUT",
                requestHeaders = "{\"X-A\":\"1\"}",
                requestBody = "{\"k\":2}",
                responseCode = 200,
                responseBody = "{}",
                responseHeaders = null,
                responseTime = 10L,
                error = null
            )
        )

        viewModel.loadHistoryById(id)
        // Room runs suspend queries on its own executor, which the test
        // scheduler cannot advance: poll until the state lands (bounded).
        var tries = 0
        while (viewModel.uiState.value.url.isEmpty() && tries++ < 100) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
        }

        val state = viewModel.uiState.value
        assertEquals("https://example.com/saved", state.url)
        assertEquals("PUT", state.method)
        assertEquals("X-A: 1", state.headers)
        assertEquals("{\"k\":2}", state.requestBody)

        dao.deleteById(id)
    }

    @Test
    fun `formatJsonResponse should format valid JSON`() = runTest {
        val unformattedJson = "{\"key\":\"value\",\"number\":123}"
        val formatted = viewModel.formatJsonResponse(unformattedJson)
        
        assertTrue(formatted.contains("\"key\""))
        assertTrue(formatted.contains("\"value\""))
        assertTrue(formatted.contains("\n")) // Should have line breaks
    }

    @Test
    fun `formatJsonResponse should return original for invalid JSON`() = runTest {
        val invalidJson = "not a json"
        val result = viewModel.formatJsonResponse(invalidJson)
        assertEquals(invalidJson, result)
    }
}
