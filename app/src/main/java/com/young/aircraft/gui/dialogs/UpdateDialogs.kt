package com.young.aircraft.gui.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.gui.openUpdatePage
import com.young.aircraft.utils.AppLog

private const val TAG = "UpdateDialogs"

/**
 * Themed "update available" prompt shared by the global activity-lifecycle
 * prompt ([com.young.aircraft.common.AircraftApplication]) and the About
 * screen's manual version check. Both paths show the same
 * remote_update_available_* strings and route through [openUpdatePage] so
 * update behavior stays identical everywhere.
 *
 * Returns the shown dialog, or null when the host theme cannot render
 * Material dialogs (e.g. plain test Activities).
 */
fun Activity.showUpdateAvailableDialog(
    latestVersion: String,
    onDismiss: (() -> Unit)? = null
): AlertDialog? {
    val dialog = try {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remote_update_available_title)
            .setMessage(
                getString(
                    R.string.remote_update_available_message,
                    BuildConfig.VERSION_NAME,
                    latestVersion
                )
            )
            .setPositiveButton(R.string.remote_update_available_action) { _, _ ->
                openUpdatePage(this)
            }
            .setNegativeButton(R.string.remote_update_later_action, null)
            .setCancelable(true)
            .showThemed(onDismiss)
    } catch (_: IllegalArgumentException) {
        AppLog.w(TAG, "Host theme cannot show update prompt; skipping")
        null
    }
    return dialog
}

/**
 * Themed "already on the latest version" confirmation for the About screen's
 * manual version check. Returns the shown dialog, or null when the host theme
 * cannot render Material dialogs.
 */
fun Activity.showAlreadyLatestVersionDialog(): AlertDialog? {
    val dialog = try {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_check_update_title)
            .setMessage(R.string.about_update_already_latest_message)
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(true)
            .showThemed()
    } catch (_: IllegalArgumentException) {
        AppLog.w(TAG, "Host theme cannot show already-latest dialog; skipping")
        null
    }
    return dialog
}
