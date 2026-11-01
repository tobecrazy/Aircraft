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
    fun apiDebugClearResetsUrlField() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("url field must be editable", screen.setText("input_url", "not-a-url"))
            assertTrue("url must be visible before clearing", screen.waitForText("not-a-url"))
            assertTrue("clear action must be clickable", screen.clickTagScrolling("btn_clear"))
            // clearAll() empties every field locally, so the typed value must vanish.
            assertTrue("clear must reset the url field", screen.waitForNoText("not-a-url"))
        }
    }

    @Test
    fun apiDebugOpensRequestHistory() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("history action must be clickable", screen.clickTagScrolling("btn_view_all_history"))
            val history = awaitResumed(ApiRequestHistoryActivity::class.java)
            try {
                assertTrue("history title", history.waitForText(R.string.api_debug_history_page_title))
            } finally {
                history.closeScreen()
            }
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
    fun appListSearchFiltersToEmptyState() {
        withScreen(AppListActivity::class.java) { screen ->
            assertTrue("search field must be editable", screen.setText(AppListSearchTag, "zzz_no_such_app_zzz"))
            // The query is applied locally, so no package query has to succeed for this.
            assertTrue("filtered empty state", screen.waitForText(R.string.develop_settings_assistant_apps_empty))
        }
    }

    @Test
    fun developSettingsOpensPdfReader() {
        withScreen(DevelopSettingsActivity::class.java) { screen ->
            assertTrue("pdf row must be clickable", screen.clickTagScrolling("btn_pdf_reader"))
            val reader = awaitResumed(PdfReaderActivity::class.java)
            try {
                assertTrue("pdf reader empty hint", reader.waitForText(R.string.pdf_reader_empty_hint))
            } finally {
                reader.closeScreen()
            }
        }
    }

    @Test
    fun developSettingsOpensApiDebugTool() {
        withScreen(DevelopSettingsActivity::class.java) { screen ->
            assertTrue("api debug row must be clickable", screen.clickTagScrolling("btn_api_debug"))
            val tool = awaitResumed(ApiDebugToolActivity::class.java)
            try {
                assertTrue("api debug title", tool.waitForText(R.string.api_debug_tool_title))
            } finally {
                tool.closeScreen()
            }
        }
    }

    @Test
    fun developSettingsBackFinishesScreen() {
        withScreen(DevelopSettingsActivity::class.java) { screen ->
            assertTrue("back action must be clickable", screen.clickTag("btn_back"))
            assertTrue("back must finish the screen", screen.waitForFinish())
        }
    }

    @Test
    fun assistantToolsOpensSystemInfo() {
        withScreen(AndroidDevAssistantToolsActivity::class.java) { screen ->
            assertTrue("system info card must be clickable", screen.clickTagScrolling("assistant_open_module_system_info"))
            val info = awaitResumed(DeviceInfoActivity::class.java)
            try {
                assertTrue("device info title", info.waitForText(R.string.title_activity_device_info))
            } finally {
                info.closeScreen()
            }
        }
    }

    @Test
    fun apiDebugRejectsInvalidUrl() {
        withScreen(ApiDebugToolActivity::class.java) { screen ->
            assertTrue("url field must be editable", screen.setText("input_url", "not-a-url"))
            // Wait for the field state to land; clicking first would validate the empty value.
            assertTrue("typed url must reach the field", screen.waitForText("not-a-url"))
            assertTrue("send action must be clickable", screen.clickTag("btn_send"))
            // Rejected before any request leaves the device.
            assertTrue("invalid-url error", screen.waitForText(R.string.api_debug_error_invalid_url))
        }
    }
}
