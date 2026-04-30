package com.young.aircraft.viewmodel

import com.young.aircraft.providers.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrivacyPolicyViewModelTest {

    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: PrivacyPolicyViewModel

    @Before
    fun setUp() {
        repository = mock()
        viewModel = PrivacyPolicyViewModel(repository)
    }

    @Test
    fun `isAlreadyAccepted returns false when not accepted`() {
        whenever(repository.isPrivacyPolicyAccepted()).thenReturn(false)
        assertFalse(viewModel.isAlreadyAccepted())
    }

    @Test
    fun `isAlreadyAccepted returns true when accepted`() {
        whenever(repository.isPrivacyPolicyAccepted()).thenReturn(true)
        assertTrue(viewModel.isAlreadyAccepted())
    }

    @Test
    fun `acceptPolicy persists acceptance`() {
        viewModel.acceptPolicy()
        verify(repository).setPrivacyPolicyAccepted(true)
    }
}
