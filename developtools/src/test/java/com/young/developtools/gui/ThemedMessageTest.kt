package com.young.developtools.gui

import android.content.ContextWrapper
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.young.developtools.R
import com.young.developtools.utils.DevPrefs
import com.young.developtools.ui.theme.BackgroundDark
import com.young.developtools.ui.theme.themeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ThemedMessageTest {
    @Test
    fun `wrapped activity snackbar updates all themes and is removed on stop`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            lateinit var message: TextView
            scenario.onActivity { activity ->
                val prefs = DevPrefs(activity)
                prefs.setTheme(DevPrefs.THEME_GREEN)
                ThemedMessage.makeText(
                    ContextWrapper(activity), R.string.invincible_mode_on, ThemedMessage.LENGTH_SHORT
                ).show()
                assertEquals(activity.getString(R.string.invincible_mode_on), snackbarText(activity))
                message = requireNotNull(activity.findViewById(com.google.android.material.R.id.snackbar_text))
                val bar = generateSequence(message.parent) { it.parent }
                    .filterIsInstance<com.google.android.material.snackbar.Snackbar.SnackbarLayout>()
                    .first()
                // Material overrides setBackgroundTintList without overriding the platform getter.
                val bitmap = android.graphics.Bitmap.createBitmap(80, 40, android.graphics.Bitmap.Config.ARGB_8888)
                val bounds = android.graphics.Rect(bar.background.bounds)
                bar.background.setBounds(0, 0, bitmap.width, bitmap.height)
                bar.background.draw(android.graphics.Canvas(bitmap))
                bar.background.bounds = bounds
                assertEquals(BackgroundDark.toArgb(), bitmap.getPixel(40, 20))
                bitmap.recycle()
                listOf(
                    DevPrefs.THEME_GREEN, DevPrefs.THEME_BLUE,
                    DevPrefs.THEME_PURPLE, DevPrefs.THEME_YELLOW,
                    DevPrefs.THEME_RED
                ).forEach { theme ->
                    prefs.setTheme(theme)
                    shadowOf(Looper.getMainLooper()).idle()
                    assertTrue(message.isShown)
                    assertEquals(themeAccent(activity, theme).toArgb(), message.currentTextColor)
                }
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertFalse(message.isShown)
        }
    }
}
