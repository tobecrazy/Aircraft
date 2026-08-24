package com.young.aircraft.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adaptive UI helper: caps content columns to a readable width on wide windows
 * (tablets / unfolded foldables) and keeps them centered. No-op on phones,
 * where the window is narrower than the cap already.
 *
 * 640dp matches the XML counterpart @dimen/content_max_width (values-sw600dp).
 */
fun Modifier.maxContentWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 640.dp)
