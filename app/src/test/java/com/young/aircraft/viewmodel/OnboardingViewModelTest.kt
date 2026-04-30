package com.young.aircraft.viewmodel

import com.young.aircraft.providers.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OnboardingViewModelTest {

    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        repository = mock()
        viewModel = OnboardingViewModel(repository)
    }

    @Test
    fun `isAlreadyCompleted returns false when not completed`() {
        whenever(repository.isOnboardingCompleted()).thenReturn(false)
        assertFalse(viewModel.isAlreadyCompleted())
    }

    @Test
    fun `isAlreadyCompleted returns true when completed`() {
        whenever(repository.isOnboardingCompleted()).thenReturn(true)
        assertTrue(viewModel.isAlreadyCompleted())
    }

    @Test
    fun `completeOnboarding persists completion`() {
        viewModel.completeOnboarding()
        verify(repository).setOnboardingCompleted(true)
    }
}
