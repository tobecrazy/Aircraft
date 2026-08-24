package com.young.aircraft.gui

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
}
