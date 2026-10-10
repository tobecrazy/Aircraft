package com.young.developtools.viewmodel

import com.young.developtools.data.LogSettings
import com.young.developtools.utils.DevLog
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LogSettingsViewModelTest {

    private val stored = MutableStateFlow(false)
    private lateinit var settings: LogSettings

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        DevLog.enabled = false
        settings = mock()
        whenever(settings.enabledFlow).thenReturn(stored)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        DevLog.enabled = false
    }

    @Test
    fun `state seeds from the runtime switch so frame one is correct`() {
        DevLog.enabled = true

        assertEquals(true, LogSettingsViewModel(settings).enabled.value)
    }

    @Test
    fun `toggle persists and emits the new value`() = runTest {
        val viewModel = LogSettingsViewModel(settings)
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.enabled.collect {} }

        viewModel.onToggle(true)
        stored.value = true

        verify(settings).setEnabled(true)
        assertEquals(true, viewModel.enabled.value)
    }
}