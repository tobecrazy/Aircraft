package com.young.aircraft.viewmodel

import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: PlayerGameDataDao
    private lateinit var viewModel: HistoryViewModel

    private val sampleRecords = listOf(
        PlayerGameData(id = 1, playerId = "pilot-1", playerName = "Ace", level = 10, score = 5000L),
        PlayerGameData(id = 2, playerId = "pilot-2", playerName = "Rookie", level = 3, score = 1200L)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates state with records and summary`() = runTest(testDispatcher) {
        whenever(dao.getAllByScoreDesc()).thenReturn(sampleRecords)

        viewModel = HistoryViewModel(dao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.recordCount)
        assertEquals(5000L, state.bestScore)
        assertEquals("Ace", state.topPilotName)
        assertEquals(10, state.topPilotLevel)
        assertEquals(sampleRecords, state.records)
    }

    @Test
    fun `empty database results in empty state`() = runTest(testDispatcher) {
        whenever(dao.getAllByScoreDesc()).thenReturn(emptyList())

        viewModel = HistoryViewModel(dao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.recordCount)
        assertNull(state.bestScore)
        assertNull(state.topPilotName)
        assertNull(state.topPilotLevel)
    }

    @Test
    fun `deleteRecord calls dao and reloads`() = runTest(testDispatcher) {
        whenever(dao.getAllByScoreDesc()).thenReturn(sampleRecords)
        viewModel = HistoryViewModel(dao)
        advanceUntilIdle()

        val remaining = listOf(sampleRecords[1])
        whenever(dao.getAllByScoreDesc()).thenReturn(remaining)

        viewModel.deleteRecord(sampleRecords[0])
        advanceUntilIdle()

        verify(dao).delete(sampleRecords[0])
        val state = viewModel.uiState.value
        assertEquals(1, state.recordCount)
        assertEquals(1200L, state.bestScore)
    }

    @Test
    fun `top pilot name falls back to truncated playerId when playerName is null`() = runTest(testDispatcher) {
        val noNameRecord = PlayerGameData(id = 1, playerId = "abcdefghijk", playerName = null, level = 5, score = 999L)
        whenever(dao.getAllByScoreDesc()).thenReturn(listOf(noNameRecord))

        viewModel = HistoryViewModel(dao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("abcdef…", state.topPilotName)
    }
}
