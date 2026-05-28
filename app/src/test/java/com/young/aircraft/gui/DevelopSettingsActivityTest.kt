package com.young.aircraft.gui

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.common.GameStateManager
import com.young.aircraft.data.SettingsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevelopSettingsActivityTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
        settingsRepository.setInvincibleModeEnabled(false)
        GameStateManager.isInvincible = false
    }

    @Test
    fun `clicking version badge 8 times toggles invincible mode`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val versionBadge = activity.findViewById<android.widget.TextView>(R.id.tv_version_badge)
                
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)

                // Click 7 times - should not toggle yet
                repeat(7) { versionBadge.performClick() }
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)

                // 8th click - should toggle ON
                versionBadge.performClick()
                assertTrue(settingsRepository.isInvincibleModeEnabled())
                assertTrue(GameStateManager.isInvincible)
                assertEquals(
                    context.getString(R.string.invincible_mode_on),
                    ShadowToast.getTextOfLatestToast()
                )

                // Another 8 clicks - should toggle OFF
                repeat(8) { versionBadge.performClick() }
                assertFalse(settingsRepository.isInvincibleModeEnabled())
                assertFalse(GameStateManager.isInvincible)
                assertEquals(
                    context.getString(R.string.invincible_mode_off),
                    ShadowToast.getTextOfLatestToast()
                )
            }
        }
    }

    @Test
    fun `clicking test crash button throws RuntimeException`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnCrash = activity.findViewById<android.view.View>(R.id.btn_test_crash)
                assertThrows(RuntimeException::class.java) {
                    btnCrash.performClick()
                }
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<android.view.View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `android developer assistant tools button opens details activity`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<android.view.View>(R.id.btn_android_dev_assistant_tools)
                button.performClick()

                val startedIntent: Intent? = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
                assertNotNull(startedIntent)
                assertEquals(
                    AndroidDevAssistantToolsActivity::class.java.name,
                    startedIntent!!.component?.className
                )
            }
        }
    }

    @Test
    fun `notification button confirms then posts QR tool notification`() {
        ActivityScenario.launch(DevelopSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(activity).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
                val notificationManager = activity.getSystemService(NotificationManager::class.java)
                shadowOf(notificationManager).setNotificationsEnabled(true)
                val button = activity.findViewById<android.view.View>(R.id.btn_notification)
                button.performClick()

                val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
                assertNotNull(dialog)
                assertEquals(
                    activity.getString(R.string.develop_settings_notification_dialog_message),
                    dialog!!.findViewById<TextView>(android.R.id.message)?.text
                )

                shadowOf(dialog).clickOn(android.R.id.button1)
                shadowOf(Looper.getMainLooper()).idle()

                val notification = shadowOf(notificationManager).getNotification(1010)
                val expectedMessage = activity.getString(
                    R.string.develop_settings_notification_message,
                    activity.getString(R.string.app_name)
                )

                assertNotNull(notification)
                assertEquals(expectedMessage, notification.extras.getCharSequence(Notification.EXTRA_TEXT))

                notification.contentIntent.send()
                val startedIntent: Intent? =
                    shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
                assertNotNull(startedIntent)
                assertEquals(
                    QRCodeToolActivity::class.java.name,
                    startedIntent!!.component?.className
                )
            }
        }
    }

    @Test
    fun `android dev assistant system info action opens device info activity`() {
        val prefs = context.getSharedPreferences("android_dev_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("module_system_info", true).apply()

        ActivityScenario.launch(AndroidDevAssistantToolsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<android.view.View>(R.id.btn_assistant_open_system_info)
                button.performClick()

                val startedIntent: Intent? = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
                assertNotNull(startedIntent)
                assertEquals(
                    DeviceInfoActivity::class.java.name,
                    startedIntent!!.component?.className
                )
            }
        }
    }
}
