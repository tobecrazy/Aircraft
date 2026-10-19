package com.young.developtools.gui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ApplicationProvider
import com.young.developtools.R
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/**
 * Installs three fake launcher apps while the sandbox application starts — before the
 * compose rule launches [AppListActivity], because the screen queries the PackageManager
 * exactly once during composition. Robolectric resolves labels from the ResolveInfo, not
 * the ApplicationInfo, so both carry the non-localized label.
 *
 * Must be public with a public no-arg constructor so Robolectric can instantiate it.
 */
class FakeLauncherAppsTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installFakeLauncherApp(this, "com.test.aircraft", "AircraftProbe", "1.0", isSystem = false)
        installFakeLauncherApp(this, "com.test.browser", "BrowserProbe", "120", isSystem = false)
        installFakeLauncherApp(this, "com.test.settings", "SettingsProbe", "30", isSystem = true)
    }
}

internal fun installFakeLauncherApp(
    context: Context,
    packageName: String,
    label: String,
    version: String,
    isSystem: Boolean
) {
    val appInfo = ApplicationInfo().apply {
        this.packageName = packageName
        nonLocalizedLabel = label
        flags = if (isSystem) ApplicationInfo.FLAG_SYSTEM else 0
    }
    val activityInfo = ActivityInfo().apply {
        name = "$packageName.EntryActivity"
        this.packageName = packageName
        applicationInfo = appInfo
    }
    val packageInfo = PackageInfo().apply {
        this.packageName = packageName
        this.versionName = version
        this.applicationInfo = appInfo
        activities = arrayOf(activityInfo)
    }
    val pm = shadowOf(context.packageManager)
    pm.installPackage(packageInfo)
    pm.addResolveInfoForIntent(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        ResolveInfo().apply {
            this.activityInfo = activityInfo
            nonLocalizedLabel = label
        }
    )
}

/**
 * Screen coverage for the full-screen installed-app browser: user/system/all filters,
 * search, the empty state, the Details action, and rememberSaveable state across
 * Activity recreation. Apps are registered into Robolectric's PackageManager shadow so
 * the real query runs in the real [AppListActivity] host; pure filter rules are covered
 * by the assistant activity test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h2000dp", application = FakeLauncherAppsTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppListActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<AppListActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `user filter is default and search narrows by label or package`() {
        awaitPackageQuery()

        composeRule.onNodeWithText("AircraftProbe").assertIsDisplayed()
        composeRule.onNodeWithText("BrowserProbe").assertIsDisplayed()
        composeRule.onNodeWithText("SettingsProbe").assertDoesNotExist()

        composeRule.onNodeWithTag(AppListSearchTag).performTextInput("browser")
        composeRule.onNodeWithText("BrowserProbe").assertIsDisplayed()
        composeRule.onNodeWithText("AircraftProbe").assertDoesNotExist()

        composeRule.onNodeWithTag(AppListSearchTag).performTextInput("zzz")
        composeRule.onNodeWithText(context.getString(R.string.develop_settings_assistant_apps_empty))
            .assertIsDisplayed()
    }

    @Test
    fun `filter chips toggle user system and all`() {
        awaitPackageQuery()

        composeRule.onNodeWithTag("${AppListFilterTagPrefix}system").performClick()
        // Robolectric native graphics can lag one frame behind a chip click, so
        // poll for the list result instead of asserting in the same tick
        waitUntilVisible("SettingsProbe")
        composeRule.onNodeWithText("SettingsProbe").assertIsDisplayed()
        composeRule.onNodeWithText("AircraftProbe").assertDoesNotExist()

        composeRule.onNodeWithTag("${AppListFilterTagPrefix}all").performClick()
        waitUntilVisible("AircraftProbe")
        composeRule.onNodeWithText("AircraftProbe").assertIsDisplayed()
        composeRule.onNodeWithText("SettingsProbe").assertIsDisplayed()

        composeRule.onNodeWithTag("${AppListFilterTagPrefix}user").performClick()
        waitUntilVisible("AircraftProbe")
        composeRule.onNodeWithText("AircraftProbe").assertIsDisplayed()
        composeRule.onNodeWithText("SettingsProbe").assertDoesNotExist()
    }

    /** Waits until [label] is present in the row list, tolerating one-frame lag. */
    private fun waitUntilVisible(label: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `details action opens the system app details page`() {
        awaitPackageQuery()

        composeRule
            .onAllNodesWithText(context.getString(R.string.develop_settings_assistant_apps_details))
            .onFirst()
            .performClick()

        val opened = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
        assertTrue(
            "details must open a page of one of the installed apps",
            opened.data?.schemeSpecificPart in setOf(
                "com.test.aircraft", "com.test.browser", "com.test.settings"
            )
        )
    }

    @Test
    fun `back icon finishes the activity`() {
        awaitPackageQuery()

        composeRule.onNodeWithTag("btn_back").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `search text survives activity recreation`() {
        awaitPackageQuery()

        composeRule
            .onNodeWithTag(AppListSearchTag)
            .performClick()
            .performTextInput("paperclip")
        awaitPackageQuery()

        composeRule.activityRule.scenario.recreate()
        awaitPackageQuery()

        composeRule
            .onNodeWithTag(AppListSearchTag)
            .assertTextContains("paperclip")
    }

    @Test
    fun `selected filter survives activity recreation`() {
        awaitPackageQuery()

        composeRule.onNodeWithTag("${AppListFilterTagPrefix}system").performClick()
        awaitPackageQuery()

        composeRule.activityRule.scenario.recreate()
        awaitPackageQuery()

        composeRule.onNodeWithTag("${AppListFilterTagPrefix}system").assertIsSelected()
        composeRule.onNodeWithText("AircraftProbe").assertDoesNotExist()
    }

    /**
     * The result area shows a CircularProgressIndicator while the package query runs and
     * Robolectric's default choreographer re-fires vsync inline — an infinite animation
     * never lets Compose go idle, so waitForIdle() spins until the 60s cap. Pause the
     * choreographer and drive a bounded number of frames instead.
     */
    private fun awaitPackageQuery() {
        ShadowChoreographer.setPaused(true)
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        composeRule.waitForIdle()
    }
}
