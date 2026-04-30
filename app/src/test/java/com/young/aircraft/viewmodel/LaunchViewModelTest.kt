package com.young.aircraft.viewmodel

import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.gui.SavedGameInfo
import com.young.aircraft.providers.SettingsRepository
import com.young.aircraft.ui.Aircraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: PlayerGameDataDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: LaunchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
        settingsRepository = mock()
        whenever(settingsRepository.getOrCreateInstallId()).thenReturn("test-player")
        viewModel = LaunchViewModel(dao, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkForSavedGame returns null when no records`() = runTest(testDispatcher) {
        whenever(dao.getByPlayerId("test-player")).thenReturn(emptyList())
        assertNull(viewModel.checkForSavedGame())
    }

    @Test
    fun `checkForSavedGame returns null for level 1 with score 0`() = runTest(testDispatcher) {
        val record = PlayerGameData(
            id = 1, playerId = "test-player", level = 1, score = 0L,
            jetPlaneIndex = 0, jetPlaneRes = Aircraft.JET_PLANES[0]
        )
        whenever(dao.getByPlayerId("test-player")).thenReturn(listOf(record))
        assertNull(viewModel.checkForSavedGame())
    }

    @Test
    fun `checkForSavedGame returns info for level greater than 1`() = runTest(testDispatcher) {
        val record = PlayerGameData(
            id = 1, playerId = "test-player", level = 5, score = 1000L,
            jetPlaneIndex = 2, jetPlaneRes = Aircraft.JET_PLANES[2]
        )
        whenever(dao.getByPlayerId("test-player")).thenReturn(listOf(record))

        val info = viewModel.checkForSavedGame()
        assertNotNull(info)
        assertEquals(5, info!!.level)
        assertEquals(2, info.jetIndex)
        assertEquals(Aircraft.JET_PLANES[2], info.jetRes)
    }

    @Test
    fun `checkForSavedGame returns info for score greater than 0`() = runTest(testDispatcher) {
        val record = PlayerGameData(
            id = 1, playerId = "test-player", level = 1, score = 500L,
            jetPlaneIndex = 1, jetPlaneRes = Aircraft.JET_PLANES[1]
        )
        whenever(dao.getByPlayerId("test-player")).thenReturn(listOf(record))

        val info = viewModel.checkForSavedGame()
        assertNotNull(info)
        assertEquals(1, info!!.level)
        assertEquals(1, info.jetIndex)
    }

    @Test
    fun `deleteSavedGame calls dao deleteByPlayerId`() = runTest(testDispatcher) {
        viewModel.deleteSavedGame()
        advanceUntilIdle()
        verify(dao).deleteByPlayerId("test-player")
    }

    @Test
    fun `checkForSavedGame resolves legacy jetPlaneRes to index`() = runTest(testDispatcher) {
        val legacyRecord = PlayerGameData(
            id = 1, playerId = "test-player", level = 3, score = 200L,
            jetPlaneIndex = 99, jetPlaneRes = Aircraft.JET_PLANES[1]
        )
        whenever(dao.getByPlayerId("test-player")).thenReturn(listOf(legacyRecord))

        val info = viewModel.checkForSavedGame()
        assertNotNull(info)
        assertEquals(1, info!!.jetIndex)
        assertEquals(Aircraft.JET_PLANES[1], info.jetRes)
    }
}
