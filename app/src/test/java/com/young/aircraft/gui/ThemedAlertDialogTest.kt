package com.young.aircraft.gui

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.young.aircraft.R
import com.young.aircraft.data.SettingsRepository
import com.young.aircraft.ui.theme.aircraftColorScheme
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import com.young.aircraft.gui.dialogs.showThemed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies showThemed paints native alerts from the persisted accent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemedAlertDialogTest {

    @Test
    fun `showThemed colors title and buttons from persisted theme`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsRepository(context).setTheme(SettingsRepository.THEME_BLUE)

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.history_delete_title)
                    .setMessage(R.string.history_delete_message)
                    .setPositiveButton(R.string.history_delete, null)
                    .setNegativeButton(R.string.history_cancel, null)
                    .showThemed()

                assertNotNull(dialog)
                assertTrue(dialog.isShowing)
                val repository = SettingsRepository(context)
                val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                listOf(
                    SettingsRepository.THEME_BLUE, SettingsRepository.THEME_GREEN,
                    SettingsRepository.THEME_PURPLE, SettingsRepository.THEME_YELLOW,
                    SettingsRepository.THEME_RED
                ).forEach { theme ->
                    positive.isEnabled = false
                    repository.setTheme(theme)
                    shadowOf(Looper.getMainLooper()).idle()
                    val colors = aircraftColorScheme(theme)
                    assertEquals(colors.primary.toArgb(),
                        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.currentTextColor)
                    assertEquals(colors.onSurface.toArgb(),
                        dialog.findViewById<TextView>(android.R.id.message)?.currentTextColor)
                    assertEquals(colors.onSurface.copy(alpha = 0.38f).toArgb(), positive.currentTextColor)
                    assertEquals(colors.primary.toArgb(), negative.currentTextColor)
                    positive.isEnabled = true
                    assertEquals(colors.primary.toArgb(), positive.currentTextColor)
                }
                dialog.dismiss()
            }
        }
    }
}
