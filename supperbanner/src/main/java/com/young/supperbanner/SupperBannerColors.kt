package com.young.supperbanner

import android.graphics.Color

/**
 * Page indicator palette. Defaults reproduce the original dark tactical look; copy() any field to
 * restyle without touching the rest.
 */
data class SupperBannerIndicatorColors(
    val textSelected: Int = DEFAULT_TEXT_SELECTED,
    val textUnselected: Int = DEFAULT_TEXT_UNSELECTED,
    val fillSelected: Int = DEFAULT_FILL_SELECTED,
    val fillUnselected: Int = DEFAULT_FILL_UNSELECTED,
    val strokeSelected: Int = DEFAULT_STROKE_SELECTED,
    val strokeUnselected: Int = DEFAULT_STROKE_UNSELECTED
) {
    companion object {
        const val DEFAULT_TEXT_SELECTED = 0xFF07100B.toInt()
        const val DEFAULT_TEXT_UNSELECTED = 0xCCFFFFFF.toInt()
        const val DEFAULT_FILL_SELECTED = 0xFF00FF88.toInt()
        const val DEFAULT_FILL_UNSELECTED = 0x442A3342
        const val DEFAULT_STROKE_SELECTED = 0xAAFFFFFF.toInt()
        const val DEFAULT_STROKE_UNSELECTED = 0x55FFFFFF.toInt()
    }
}

/**
 * Every color [SupperBannerView] paints with. The parameterless constructor keeps the historical
 * look, so existing hosts see no change until they call `setColors`.
 *
 * A host that also installs an indicator customizer keeps winning on the indicators: the customizer
 * runs after these defaults are applied.
 */
data class SupperBannerColors(
    val background: Int = DEFAULT_BACKGROUND,
    val border: Int = DEFAULT_BORDER,
    val scrimStart: Int = DEFAULT_SCRIM_START,
    val scrimEnd: Int = DEFAULT_SCRIM_END,
    val title: Int = DEFAULT_TITLE,
    val description: Int = DEFAULT_DESCRIPTION,
    val indicator: SupperBannerIndicatorColors = SupperBannerIndicatorColors()
) {
    companion object {
        const val DEFAULT_BACKGROUND = 0xFF151A24.toInt()
        const val DEFAULT_BORDER = 0x2AFFFFFF
        val DEFAULT_SCRIM_START = Color.TRANSPARENT
        const val DEFAULT_SCRIM_END = 0xCC050812.toInt()
        const val DEFAULT_TITLE = 0xFFFFFFFF.toInt()
        const val DEFAULT_DESCRIPTION = 0xFFCBD5E8.toInt()
    }
}
