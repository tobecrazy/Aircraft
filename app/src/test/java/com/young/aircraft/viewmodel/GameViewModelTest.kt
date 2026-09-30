package com.young.aircraft.viewmodel

import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.GameMode
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: PlayerGameDataDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
        settingsRepository = mock()
        whenever(settingsRepository.getDifficulty()).thenReturn(GameDifficulty.NORMAL)
        viewModel = GameViewModel(dao, "test-player", settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calculateScore returns kills times 100`() {
        assertEquals(0L, viewModel.calculateScore(0))
        assertEquals(100L, viewModel.calculateScore(1))
        assertEquals(5000L, viewModel.calculateScore(50))
    }

    @Test
    fun `shouldAutoSaveOnExit returns false for level 1 with zero kills`() {
        assertFalse(viewModel.shouldAutoSaveOnExit(level = 1, totalKills = 0))
    }

    @Test
    fun `shouldAutoSaveOnExit returns true when level greater than 1`() {
        assertTrue(viewModel.shouldAutoSaveOnExit(level = 2, totalKills = 0))
    }

    @Test
    fun `shouldAutoSaveOnExit returns true when kills greater than 0`() {
        assertTrue(viewModel.shouldAutoSaveOnExit(level = 1, totalKills = 5))
    }

    @Test
    fun `getDifficulty delegates to repository`() {
        whenever(settingsRepository.getDifficulty()).thenReturn(GameDifficulty.HARD)
        assertEquals(GameDifficulty.HARD, viewModel.getDifficulty())
    }

    @Test
    fun `saveGameData delegates atomic replacement with difficulty from repository`() = runTest(testDispatcher) {
        viewModel.saveGameData(
            level = 3,
            totalKills = 25,
            puzzleScore = 200,
            puzzleLevel = 2,
            gameMode = GameMode.AIR_BATTLE,
            jetPlaneResId = 123,
            jetPlaneIndex = 1
        )

        val captor = argumentCaptor<PlayerGameData>()
        verify(dao).replaceForPlayer(captor.capture())

        val inserted = captor.firstValue
        assertEquals("test-player", inserted.playerId)
        assertEquals(3, inserted.level)
        assertEquals(3, inserted.airBattleLevel)
        assertEquals(2, inserted.puzzleLevel)
        assertEquals(GameMode.AIR_BATTLE.name, inserted.gameMode)
        assertEquals(2500L, inserted.score)
        assertEquals(200L, inserted.puzzleScore)
        assertEquals(25, inserted.totalKills)
        assertEquals(123, inserted.jetPlaneRes)
        assertEquals(1, inserted.jetPlaneIndex)
        assertEquals("1.0", inserted.difficulty)
    }

    @Test
    fun `saveGameData leaves existing playerName merge to transactional dao`() = runTest(testDispatcher) {
        viewModel.saveGameData(
            level = 5,
            totalKills = 10,
            puzzleScore = 0,
            puzzleLevel = 5,
            gameMode = GameMode.AIR_BATTLE,
            jetPlaneResId = 0,
            jetPlaneIndex = 0
        )

        val captor = argumentCaptor<PlayerGameData>()
        verify(dao).replaceForPlayer(captor.capture())
        assertNull(captor.firstValue.playerName)
    }

    @Test
    fun `saveGameData passes provided playerName into atomic replacement`() = runTest(testDispatcher) {
        viewModel.saveGameData(
            level = 10,
            totalKills = 100,
            puzzleScore = 500,
            puzzleLevel = 9,
            gameMode = GameMode.PUZZLE,
            jetPlaneResId = 0,
            jetPlaneIndex = 0,
            playerName = "NewHero"
        )

        val captor = argumentCaptor<PlayerGameData>()
        verify(dao).replaceForPlayer(captor.capture())
        assertEquals("NewHero", captor.firstValue.playerName)
    }

    @Test
    fun `saveGameData uses hard difficulty from repository`() = runTest(testDispatcher) {
        whenever(settingsRepository.getDifficulty()).thenReturn(GameDifficulty.HARD)
        viewModel.saveGameData(
            level = 1,
            totalKills = 10,
            puzzleScore = 90,
            puzzleLevel = 1,
            gameMode = GameMode.AIR_BATTLE,
            jetPlaneResId = 0,
            jetPlaneIndex = 0
        )

        val captor = argumentCaptor<PlayerGameData>()
        verify(dao).replaceForPlayer(captor.capture())
        assertEquals("0.8", captor.firstValue.difficulty)
        assertEquals(1000L, captor.firstValue.score)
    }

    @Test
    fun `deletePlayerData calls dao deleteByPlayerId`() = runTest(testDispatcher) {
        viewModel.deletePlayerData()
        verify(dao).deleteByPlayerId("test-player")
    }
}
