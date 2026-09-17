package com.young.aircraft.gui.dialogs

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.themeAccent

/** Keeps native alert buttons and accessibility while following the selected accent. */
fun MaterialAlertDialogBuilder.showThemed(): AlertDialog {
    val repository = SettingsRepository(context)
    val density = context.resources.displayMetrics.density
    val background = GradientDrawable().apply {
        setColor(BackgroundDark.toArgb())
        cornerRadius = 20 * density
    }
    val dialog = setBackground(background).create()
    fun applyTheme() {
        val accent = themeAccent(repository.getTheme()).toArgb()
        background.setStroke((1.5f * density).toInt().coerceAtLeast(1), accent)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(accent)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(TextBright.toArgb())
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { dialog.getButton(it)?.setTextColor(accent) }
    }
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == SettingsRepository.KEY_THEME) applyTheme()
    }
    dialog.setOnShowListener {
        repository.registerListener(listener)
        applyTheme()
    }
    dialog.setOnDismissListener { repository.unregisterListener(listener) }
    dialog.show()
    return dialog
}
