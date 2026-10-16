package com.young.aircraft.utils

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInterruptionShieldTest {

    @Test
    fun `restores saved filter only while shield filter is still active`() {
        assertTrue(
            shouldRestorePreviousFilter(
                currentFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
                previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
        assertFalse(
            shouldRestorePreviousFilter(
                currentFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL
            )
        )
        assertFalse(
            shouldRestorePreviousFilter(
                currentFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
                previousFilter = NotificationManager.INTERRUPTION_FILTER_NONE
            )
        )
    }
}
