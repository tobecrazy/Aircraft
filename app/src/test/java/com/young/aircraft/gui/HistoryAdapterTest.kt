package com.young.aircraft.gui

import android.graphics.Color
import android.view.View
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.PlayerGameData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryAdapterTest {

    @Test
    fun `top record shows badge and gold score styling`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Aircraft_Common
        )
        val parent = FrameLayout(context)
        val adapter = HistoryAdapter(
            mutableListOf(
                PlayerGameData(playerId = "ace-pilot", playerName = "Ace Pilot", level = 10, score = 12_345),
                PlayerGameData(playerId = "wingman", playerName = "Wingman", level = 9, score = 9_999)
            )
        ) { }

        val topHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(topHolder, 0)

        assertEquals(View.VISIBLE, topHolder.binding.topRecordGroup.visibility)
        assertEquals(View.VISIBLE, topHolder.binding.imageTopRecordBadge.visibility)
        assertEquals(Color.parseColor("#FFD45A"), topHolder.binding.textScore.currentTextColor)
        assertEquals(
            context.getString(R.string.level, "10"),
            topHolder.binding.textLevel.text.toString()
        )

        val secondHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(secondHolder, 1)

        assertEquals(View.GONE, secondHolder.binding.topRecordGroup.visibility)
        assertEquals(View.GONE, secondHolder.binding.imageTopRecordBadge.visibility)
    }
}
