package com.young.developtools.gui

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.young.developtools.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the developer hub and its tool pages. Every one of
 * these activities is gated by [com.young.developtools.utils.DeveloperMode],
 * which is always on in debug builds, so they open without unlocking anything.
 * Read-only: no setting is toggled and no request is sent.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DevelopScreensTest {

    /**
     * [ContactsActivity] requests both contact permissions on its first frame.
     * Grant them up front so the system dialog cannot cover the page; the
     * permissions are declared in this module's manifest, so the grant applies.
     */
    @Before
    fun grantContactsPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).forEach { permission ->
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(packageName, permission) }
        }
    }

    @Test
    fun developSettingsScreenRenders() {
        withScreen(DevelopSettingsActivity::class.java) { screen ->
            assertTrue("develop settings title", screen.waitForText(R.string.develop_settings_title))
            assertTrue("banner section", screen.waitForText(R.string.develop_settings_supper_banner_title))
        }
    }

    @Test
    fun assistantToolsScreenRenders() {
        withScreen(AndroidDevAssistantToolsActivity::class.java) { screen ->
            assertTrue("assistant title", screen.waitForText(R.string.android_dev_assistant_tools_title))
        }
    }

    @Test
    fun appListScreenRenders() {
        withScreen(AppListActivity::class.java) { screen ->
            assertTrue("installed apps title", screen.waitForText(R.string.develop_settings_assistant_apps_title))
        }
    }

    @Test
    fun apiDebugToolScreenRenders() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("api debug title", screen.waitForText(R.string.api_debug_tool_title))
        }
    }

    @Test
    fun apiDebugRejectsEmptyUrl() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("send action", screen.waitForText(R.string.api_debug_send_button))
            assertTrue("send action must be clickable", screen.clickTag("btn_send"))
            // The validation gate answers without touching the network.
            assertTrue("empty-url error", screen.waitForText(R.string.api_debug_error_empty_url))
        }
    }

    @Test
    fun apiRequestHistoryScreenRenders() {
        withScreen(ApiRequestHistoryActivity::class.java) { screen ->
            assertTrue("request history title", screen.waitForText(R.string.api_debug_history_page_title))
        }
    }

    @Test
    fun contactsScreenRenders() {
        withScreen(ContactsActivity::class.java) { screen ->
            assertTrue("contacts title", screen.waitForText(R.string.develop_settings_assistant_module_contacts))
        }
    }

    @Test
    fun apiDebugRejectsInvalidUrl() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("url field must be editable", screen.setText("input_url", "not-a-url"))
            assertTrue("send action must be clickable", screen.clickTag("btn_send"))
            // Rejected before any request leaves the device.
            assertTrue("invalid-url error", screen.waitForText(R.string.api_debug_error_invalid_url))
        }
    }
}
