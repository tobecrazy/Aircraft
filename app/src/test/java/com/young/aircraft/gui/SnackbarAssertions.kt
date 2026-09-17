package com.young.aircraft.gui

import android.app.Activity
import android.os.Looper
import android.widget.TextView
import org.robolectric.Shadows.shadowOf
import java.time.Duration

internal fun snackbarText(activity: Activity): String? {
    // Finish entrance/exit transitions, including replacement by a second message.
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    return activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?.takeIf { it.isShown }?.text?.toString()
}
