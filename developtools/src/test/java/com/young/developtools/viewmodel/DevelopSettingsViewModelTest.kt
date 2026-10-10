package com.young.developtools.viewmodel

import com.young.developtools.utils.DevPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DevelopSettingsViewModelTest {

    private lateinit var prefs: DevPrefs
    private lateinit var viewModel: DevelopSettingsViewModel

    @Before
    fun setUp() {
        prefs = mock()
        viewModel = DevelopSettingsViewModel(prefs)
    }

    @Test
    fun `isInvincibleModeEnabled returns prefs value`() {
        whenever(prefs.isInvincibleModeEnabled()).thenReturn(false)
        assertFalse(viewModel.isInvincibleModeEnabled())

        whenever(prefs.isInvincibleModeEnabled()).thenReturn(true)
        assertTrue(viewModel.isInvincibleModeEnabled())
    }

    @Test
    fun `setInvincibleModeEnabled persists value`() {
        viewModel.setInvincibleModeEnabled(true)
        verify(prefs).setInvincibleModeEnabled(true)
    }

    @Test
    fun `setInvincibleModeEnabled persists false`() {
        viewModel.setInvincibleModeEnabled(false)
        verify(prefs).setInvincibleModeEnabled(false)
    }
}
