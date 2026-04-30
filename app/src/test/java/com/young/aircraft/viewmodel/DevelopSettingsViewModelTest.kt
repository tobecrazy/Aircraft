package com.young.aircraft.viewmodel

import com.young.aircraft.providers.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DevelopSettingsViewModelTest {

    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: DevelopSettingsViewModel

    @Before
    fun setUp() {
        repository = mock()
        viewModel = DevelopSettingsViewModel(repository)
    }

    @Test
    fun `isInvincibleModeEnabled returns repository value`() {
        whenever(repository.isInvincibleModeEnabled()).thenReturn(false)
        assertFalse(viewModel.isInvincibleModeEnabled())

        whenever(repository.isInvincibleModeEnabled()).thenReturn(true)
        assertTrue(viewModel.isInvincibleModeEnabled())
    }

    @Test
    fun `setInvincibleModeEnabled persists value`() {
        viewModel.setInvincibleModeEnabled(true)
        verify(repository).setInvincibleModeEnabled(true)
    }

    @Test
    fun `setInvincibleModeEnabled persists false`() {
        viewModel.setInvincibleModeEnabled(false)
        verify(repository).setInvincibleModeEnabled(false)
    }
}
