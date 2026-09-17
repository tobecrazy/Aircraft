package com.young.aircraft.gui.dialogs

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.aircraftColorScheme

/** Keeps native alert buttons and accessibility while following the selected accent. */
fun MaterialAlertDialogBuilder.showThemed(): AlertDialog {
    val repository = SettingsRepository(context)
    val density = context.resources.displayMetrics.density
    val background = GradientDrawable().apply {
        cornerRadius = 20 * density
    }
    val dialog = setBackground(background).create()
    fun applyTheme() {
        val colors = aircraftColorScheme(repository.getTheme())
        background.setColor(colors.surface.toArgb())
        background.setStroke((1.5f * density).toInt().coerceAtLeast(1), colors.outline.toArgb())
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(colors.primary.toArgb())
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(colors.onSurface.toArgb())
        val buttonColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.onSurface.copy(alpha = 0.38f).toArgb(), colors.primary.toArgb())
        )
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { dialog.getButton(it)?.setTextColor(buttonColors) }
    }
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == SettingsRepository.KEY_THEME) applyTheme()
    }
    dialog.setOnDismissListener { repository.unregisterListener(listener) }
    dialog.show()
    repository.registerListener(listener)
    applyTheme()
    return dialog
}
