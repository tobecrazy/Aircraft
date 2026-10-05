package com.young.aircraft.gui

import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseAircraftActivityTest {

    @Before
    fun setUp() {
        BaseAircraftActivityTestState.reset()
    }

    @After
    fun tearDown() {
        BaseAircraftActivityTestState.reset()
    }

    @Test
    fun `initializes view model before UI`() {
        Robolectric.buildActivity(BaseAircraftActivityTestHost::class.java).setup()

        assertEquals(listOf("viewModel", "ui"), BaseAircraftActivityTestState.calls)
    }

    @Test
    fun `skips UI initialization when view model initialization finishes activity`() {
        BaseAircraftActivityTestState.finishDuringViewModelInitialization = true

        val activity = Robolectric.buildActivity(BaseAircraftActivityTestHost::class.java)
            .create()
            .get()

        assertTrue(activity.isFinishing)
        assertEquals(listOf("viewModel"), BaseAircraftActivityTestState.calls)
    }
}

private object BaseAircraftActivityTestState {
    val calls = mutableListOf<String>()
    var finishDuringViewModelInitialization = false

    fun reset() {
        calls.clear()
        finishDuringViewModelInitialization = false
    }
}

class BaseAircraftActivityTestHost : BaseAircraftActivity() {
    override fun initializeViewModel(savedInstanceState: Bundle?) {
        BaseAircraftActivityTestState.calls += "viewModel"
        if (BaseAircraftActivityTestState.finishDuringViewModelInitialization) finish()
    }

    override fun initializeUI() {
        BaseAircraftActivityTestState.calls += "ui"
    }
}
