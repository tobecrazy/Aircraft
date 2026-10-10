package com.young.developtools.gui.dialogs

import androidx.compose.ui.graphics.Color

/**
 * Dialog colors derived from the current theme's accent.
 *
 * Copy of the app's GameDialogPalette shape (game settlement screens keep their own).
 * InfoDialogContent is the only consumer here.
 */
data class DevDialogPalette(
    val titleColor: Color,
    val dividerColor: Color,
    val badgeContainer: Color,
    val badgeBorder: Color,
    val statCardContainer: Color,
    val statCardBorder: Color,
    val statLabelColor: Color,
    val positiveButtonContainer: Color
)

internal fun devDialogPalette(accent: Color) = DevDialogPalette(
    titleColor = accent,
    dividerColor = accent.copy(alpha = 0x44 / 255f),
    badgeContainer = accent.copy(alpha = 0x26 / 255f),
    badgeBorder = accent.copy(alpha = 0x66 / 255f),
    statCardContainer = accent.copy(alpha = 0.09f),
    statCardBorder = accent.copy(alpha = 0.13f),
    statLabelColor = accent.copy(alpha = 0.8f),
    positiveButtonContainer = accent
)
