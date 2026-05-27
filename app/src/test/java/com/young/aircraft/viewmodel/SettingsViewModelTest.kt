package com.young.aircraft.viewmodel

import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.PlayerGameDataDao
import com.young.aircraft.data.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsViewModelTest {

    private lateinit var repository: SettingsRepository
    private lateinit var gameDataDao: PlayerGameDataDao
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        repository = mock()
        gameDataDao = mock()
        whenever(repository.getDifficulty()).thenReturn(GameDifficulty.NORMAL)
        whenever(repository.isBackgroundSoundEnabled()).thenReturn(true)
        whenever(repository.isCombatSoundEnabled()).thenReturn(true)
        whenever(repository.isHitShakeEffectEnabled()).thenReturn(true)
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(repository, gameDataDao)

    @Test
    fun `initial state reflects repository values`() {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(GameDifficulty.NORMAL, state.difficulty)
        assertTrue(state.bgSoundEnabled)
        assertTrue(state.combatSoundEnabled)
        assertTrue(state.hitShakeEnabled)
        assertEquals(3, state.enabledSoundCount)
    }

    @Test
    fun `setDifficulty persists and updates state`() {
        viewModel = createViewModel()

        whenever(repository.getDifficulty()).thenReturn(GameDifficulty.HARD)
        viewModel.setDifficulty(GameDifficulty.HARD)

        verify(repository).setDifficulty(GameDifficulty.HARD)
        assertEquals(GameDifficulty.HARD, viewModel.uiState.value.difficulty)
    }

    @Test
    fun `toggling sound updates enabledSoundCount`() {
        viewModel = createViewModel()

        whenever(repository.isBackgroundSoundEnabled()).thenReturn(false)
        viewModel.setBgSoundEnabled(false)

        verify(repository).setBackgroundSoundEnabled(false)
        val state = viewModel.uiState.value
        assertFalse(state.bgSoundEnabled)
        assertEquals(2, state.enabledSoundCount)
    }

    @Test
    fun `all sounds off results in zero count`() {
        whenever(repository.isBackgroundSoundEnabled()).thenReturn(false)
        whenever(repository.isCombatSoundEnabled()).thenReturn(false)
        whenever(repository.isHitShakeEffectEnabled()).thenReturn(false)

        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(0, state.enabledSoundCount)
        assertFalse(state.bgSoundEnabled)
        assertFalse(state.combatSoundEnabled)
        assertFalse(state.hitShakeEnabled)
    }

    @Test
    fun `setCombatSoundEnabled persists and updates state`() {
        viewModel = createViewModel()

        whenever(repository.isCombatSoundEnabled()).thenReturn(false)
        viewModel.setCombatSoundEnabled(false)

        verify(repository).setCombatSoundEnabled(false)
        assertFalse(viewModel.uiState.value.combatSoundEnabled)
        assertEquals(2, viewModel.uiState.value.enabledSoundCount)
    }

    @Test
    fun `setHitShakeEnabled persists and updates state`() {
        viewModel = createViewModel()

        whenever(repository.isHitShakeEffectEnabled()).thenReturn(false)
        viewModel.setHitShakeEnabled(false)

        verify(repository).setHitShakeEffectEnabled(false)
        assertFalse(viewModel.uiState.value.hitShakeEnabled)
    }

    @Test
    fun `clearCachedGameData deletes saved records and cached files`() = runTest {
        viewModel = createViewModel()

        viewModel.clearCachedGameData()

        verify(gameDataDao).deleteAll()
        verify(repository).clearCachedGameData()
    }

    @Test
    fun `getCachedGameDataSizeBytes returns repository size`() = runTest {
        whenever(repository.getCachedGameDataSizeBytes()).thenReturn(2048L)
        viewModel = createViewModel()

        assertEquals(2048L, viewModel.getCachedGameDataSizeBytes())
        verify(repository).getCachedGameDataSizeBytes()
    }
}
