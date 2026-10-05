package com.young.aircraft.gui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import android.os.Looper
import com.young.aircraft.R
import com.young.aircraft.gui.dialogs.InfoCopyButtonTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h2000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidDevAssistantToolsActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<AndroidDevAssistantToolsActivity>()

    private val assistantPrefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("android_dev_assistant_prefs", Context.MODE_PRIVATE)

    private fun tick() {
        composeTestRule.mainClock.advanceTimeBy(32)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `toggle persists to assistant prefs and disables its action button`() {
        tick()

        val openTag = "assistant_open_${AndroidDevAssistantToolsActivity.MODULE_SYSTEM_INFO}"
        composeTestRule.onNodeWithTag(openTag).assertIsEnabled()

        composeTestRule
            .onNodeWithTag("assistant_switch_${AndroidDevAssistantToolsActivity.MODULE_SYSTEM_INFO}")
            .performClick()
        tick()

        assertFalse(
            assistantPrefs.getBoolean(AndroidDevAssistantToolsActivity.MODULE_SYSTEM_INFO, true)
        )
        composeTestRule.onNodeWithTag(openTag).assertIsNotEnabled()
    }

    @Test
    fun `system info module opens DeviceInfoActivity`() {
        tick()

        composeTestRule
            .onNodeWithTag("assistant_open_${AndroidDevAssistantToolsActivity.MODULE_SYSTEM_INFO}")
            .performClick()
        tick()

        val nextIntent = shadowOf(composeTestRule.activity).nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(DeviceInfoActivity::class.java.name, nextIntent.component?.className)
    }

    @Test
    fun `disabled module does not launch its action`() {
        val key = AndroidDevAssistantToolsActivity.MODULE_SYSTEM_INFO
        assistantPrefs.edit().putBoolean(key, false).commit()
        tick()

        composeTestRule.onNodeWithTag("assistant_open_$key").performClick()
        tick()

        assertEquals("disabled module must not navigate", null,
            shadowOf(composeTestRule.activity).nextStartedActivity)
        assertFalse(assistantPrefs.getBoolean(key, true))
    }

    @Test
    fun `back finishes the activity`() {
        tick()

        composeTestRule.onNodeWithTag("btn_back").performClick()
        tick()

        assertTrue(composeTestRule.activity.isFinishing)
    }

    @Test
    fun `kernel info module shows a dialog instead of navigating`() {
        tick()

        composeTestRule
            .onNodeWithTag(ModuleListTag)
            .performScrollToNode(hasTestTag("assistant_open_${AndroidDevAssistantToolsActivity.MODULE_KERNEL_INFO}"))
        composeTestRule
            .onNodeWithTag("assistant_open_${AndroidDevAssistantToolsActivity.MODULE_KERNEL_INFO}")
            .performClick()
        composeTestRule.waitForIdle()

        val info = readKernelInfo()
        assertTrue("release must never be blank", info.release.isNotBlank())
        assertEquals("kernel module shows a dialog, it does not navigate", null,
            shadowOf(composeTestRule.activity).nextStartedActivity)
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `browser engine module reports a webview package and chromium version`() {
        val info = readBrowserEngineInfo(ApplicationProvider.getApplicationContext())
        assertTrue("webViewPackage must never be blank", info.webViewPackage.isNotBlank())
        assertTrue("chromiumMajor must be a number or 'unknown'",
            info.chromiumMajor == "unknown" || info.chromiumMajor.all { it.isDigit() })
    }

    @Test
    fun `browser engine module shows a dialog instead of navigating`() {
        tick()

        composeTestRule
            .onNodeWithTag(ModuleListTag)
            .performScrollToNode(hasTestTag("assistant_open_${AndroidDevAssistantToolsActivity.MODULE_BROWSER_ENGINE}"))
        composeTestRule
            .onNodeWithTag("assistant_open_${AndroidDevAssistantToolsActivity.MODULE_BROWSER_ENGINE}")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("browser module shows a dialog, it does not navigate", null,
            shadowOf(composeTestRule.activity).nextStartedActivity)
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `kernel dialog copy button puts the kernel dump on the clipboard`() {
        openModuleDialog(AndroidDevAssistantToolsActivity.MODULE_KERNEL_INFO)

        composeTestRule.onNodeWithTag(InfoCopyButtonTag).performClick()
        tick()

        val clip = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        val expected = composeTestRule.activity.getString(
            R.string.develop_settings_assistant_kernel_dialog_message,
            readKernelInfo().release,
            readKernelInfo().machine,
            readKernelInfo().fullVersion
        )
        assertEquals(expected, copied)
        assertTrue("copying must not close the dialog", ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun `app browser module shows the in-app list dialog instead of navigating`() {
        openModuleDialog(AndroidDevAssistantToolsActivity.MODULE_APP_BROWSER)

        assertEquals("app browser module shows a dialog, it does not navigate", null,
            shadowOf(composeTestRule.activity).nextStartedActivity)
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun `app filter splits user apps from system apps and matches name or package`() {
        val apps = listOf(
            InstalledApp("com.young.aircraft", "Aircraft", "1.0", isSystem = false),
            InstalledApp("com.android.chrome", "Chrome", "120", isSystem = false),
            InstalledApp("com.android.settings", "Settings", "30", isSystem = true)
        )

        assertEquals(
            listOf("com.young.aircraft", "com.android.chrome"),
            filterApps(apps, "", AppFilter.USER).map { it.packageName }
        )
        assertEquals(
            listOf("com.android.settings"),
            filterApps(apps, "", AppFilter.SYSTEM).map { it.packageName }
        )
        assertEquals(3, filterApps(apps, "", AppFilter.ALL).size)
        assertEquals(
            listOf("com.android.chrome"),
            filterApps(apps, "chro", AppFilter.USER).map { it.packageName }
        )
        assertEquals(
            listOf("com.android.settings"),
            filterApps(apps, "com.android.set", AppFilter.ALL).map { it.packageName }
        )
        assertTrue(filterApps(apps, "nothing-here", AppFilter.ALL).isEmpty())
    }

    @Test
    fun `app log switch row is on the tools list`() {
        tick()

        composeTestRule
            .onNodeWithTag(ModuleListTag)
            .performScrollToNode(hasTestTag("assistant_switch_app_logs"))
        composeTestRule.onNodeWithTag("assistant_switch_app_logs").assertIsDisplayed()
    }

    private fun openModuleDialog(prefKey: String) {
        tick()
        composeTestRule
            .onNodeWithTag(ModuleListTag)
            .performScrollToNode(hasTestTag("assistant_open_$prefKey"))
        composeTestRule.onNodeWithTag("assistant_open_$prefKey").performClick()
        // The app-browser dialog shows a CircularProgressIndicator while it queries the package
        // manager, and Robolectric's default choreographer re-fires vsync inline — an infinite
        // animation never lets Compose go idle, so waitForIdle() spins until the 60s cap.
        // Pause the choreographer and drive a bounded number of frames instead.
        ShadowChoreographer.setPaused(true)
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        assertNotNull(ShadowDialog.getLatestDialog())
    }
}
