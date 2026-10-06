package com.young.aircraft.common

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers which launcher alias ends up enabled. Explicit [pinToDefault] values keep
 * these tests deterministic in every build variant; the debug default (pinned) is
 * what unit tests compile against, but release behavior is verified through the
 * unpinned path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LauncherIconManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packageManager = context.packageManager
    }

    private fun alias(index: Int): ComponentName =
        ComponentName(context, "${context.packageName}.gui.LauncherIcon$index")

    private fun stateOf(index: Int): Int = packageManager.getComponentEnabledSetting(alias(index))

    @Test
    fun `unpinned apply selects the requested variant`() {
        LauncherIconManager.apply(context, 3, pinToDefault = false)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(3))
        listOf(1, 2, 4, 5).forEach { index ->
            assertEquals(
                "LauncherIcon$index",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                stateOf(index)
            )
        }
    }

    @Test
    fun `unpinned apply falls back to icon one for out-of-range variants`() {
        listOf(0, 6, -1).forEach { variant ->
            LauncherIconManager.apply(context, variant, pinToDefault = false)

            assertEquals(
                "variant $variant",
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                stateOf(1)
            )
        }
    }

    @Test
    fun `pinned apply keeps icon one regardless of variant`() {
        // Regression test: a Remote Config value (or stale component state) must never
        // invalidate the IDE launch target (LauncherIcon1) in debug builds.
        LauncherIconManager.apply(context, 4, pinToDefault = true)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(1))
        listOf(2, 3, 4, 5).forEach { index ->
            assertEquals(
                "LauncherIcon$index",
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                stateOf(index)
            )
        }
    }

    @Test
    fun `pinned apply re-enables icon one after another variant was selected`() {
        LauncherIconManager.apply(context, 2, pinToDefault = false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(2))

        LauncherIconManager.apply(context, 2, pinToDefault = true)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(1))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateOf(2))
    }
}
