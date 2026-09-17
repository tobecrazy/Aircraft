package com.young.aircraft.gui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.themeAccent

/** Foreground messages use the app palette rather than the system-controlled Toast UI. */
class ThemedMessage private constructor(
    private val context: Context,
    private val text: CharSequence,
    private val duration: Int
) {
    fun show() {
        var host = context
        while (host is ContextWrapper && host !is Activity) {
            val base = host.baseContext
            if (base === host) return
            host = base
        }
        val activity = host as? Activity ?: return
        val owner = activity as? LifecycleOwner ?: return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed ||
                !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) return@runOnUiThread
            val root = activity.findViewById<android.view.View>(android.R.id.content)
            if (!root.isAttachedToWindow) return@runOnUiThread
            val snackbar = Snackbar.make(root, text, duration)
            val repository = SettingsRepository(activity)
            fun applyTheme() {
                val accent = themeAccent(repository.getTheme()).toArgb()
                snackbar.setBackgroundTint(BackgroundDark.toArgb())
                    .setTextColor(accent)
                    .setActionTextColor(accent)
            }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key == SettingsRepository.KEY_THEME) applyTheme()
            }
            val observer = object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    repository.unregisterListener(listener)
                    owner.lifecycle.removeObserver(this)
                    snackbar.dismiss()
                }
            }
            snackbar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                    repository.unregisterListener(listener)
                    owner.lifecycle.removeObserver(observer)
                }
            })
            repository.registerListener(listener)
            owner.lifecycle.addObserver(observer)
            applyTheme()
            snackbar.show()
        }
    }

    companion object {
        // Preserve Toast's short duration; Snackbar.LENGTH_SHORT only lasts 1500 ms.
        const val LENGTH_SHORT = 2000

        fun makeText(context: Context, text: CharSequence, duration: Int) =
            ThemedMessage(context, text, duration)

        fun makeText(context: Context, @StringRes text: Int, duration: Int) =
            makeText(context, context.getText(text), duration)
    }
}
