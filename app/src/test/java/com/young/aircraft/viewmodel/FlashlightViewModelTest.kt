package com.young.aircraft.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashlightViewModelTest {

    @Test
    fun `brightnessToStrengthLevel maps percent to supported camera range`() {
        assertEquals(1, FlashlightViewModel.brightnessToStrengthLevel(0f, 5))
        assertEquals(3, FlashlightViewModel.brightnessToStrengthLevel(0.5f, 5))
        assertEquals(5, FlashlightViewModel.brightnessToStrengthLevel(1f, 5))
    }

    @Test
    fun `brightnessToStrengthLevel clamps invalid values`() {
        assertEquals(1, FlashlightViewModel.brightnessToStrengthLevel(-1f, 10))
        assertEquals(10, FlashlightViewModel.brightnessToStrengthLevel(2f, 10))
        assertEquals(1, FlashlightViewModel.brightnessToStrengthLevel(0.5f, 1))
    }

    @Test
    fun `sos pattern encodes three dots three dashes and three dots`() {
        val onPulses = FlashlightViewModel.SOS_PATTERN.filter { it.torchOn }

        assertEquals(listOf(1, 1, 1, 3, 3, 3, 1, 1, 1), onPulses.map { it.units })
        assertTrue(FlashlightViewModel.SOS_PATTERN.first().torchOn)
        assertTrue(FlashlightViewModel.SOS_PATTERN.last().torchOn)
        assertFalse(FlashlightViewModel.SOS_PATTERN.any { it.units <= 0 })
    }
}
