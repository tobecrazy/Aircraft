package com.young.aircraft.gui

import android.app.Application
import androidx.test.core.app.ActivityScenario
import com.young.aircraft.R
import com.young.aircraft.common.RepoUpdateConfig
import com.young.aircraft.common.RepoUpdateConfigStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the blocking update screen with a pinned minimum version.
 *
 * This cannot live in the connected suite: [RepoUpdateConfigStore] prefers a
 * Firebase REMOTE `minimum_version` over the cached repo file, so on a runner
 * with working Firebase connectivity the forced value is ignored, the gate in
 * `AircraftApplication` closes the screen right after it resumes, and nothing
 * is left to assert. Robolectric has no activated Remote Config, so the cached
 * value is what the screen reads.
 *
 * `application = Application::class` keeps the real application out of the
 * test: its startup refresh would hit the network and install the gate hook.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w420dp-h920dp")
class MandatoryUpdateActivityTest {

    @Test
    fun `renders required update details`() {
        withPinnedMinimumVersion("99.0.0") {
            ActivityScenario.launch(MandatoryUpdateActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val texts = findAllNodes(activity.window.decorView.findSemanticsOwner()?.rootSemanticsNode
                        ?: error("Compose semantics tree is missing"))
                        .mapNotNull { it.displayText() }

                    assertTrue(activity.getString(R.string.remote_update_required_title) in texts)
                    assertTrue(activity.getString(R.string.remote_update_current_version) in texts)
                    assertTrue(texts.any { it.contains("99.0.0") })
                }
            }
        }
    }

    /** Pins the cached config for [block]; the store keeps its setter private. */
    private fun withPinnedMinimumVersion(version: String, block: () -> Unit) {
        val field = RepoUpdateConfigStore::class.java
            .getDeclaredField("cached")
            .apply { isAccessible = true }
        val previous = field.get(RepoUpdateConfigStore)
        field.set(RepoUpdateConfigStore, RepoUpdateConfig(minimumVersion = version))
        try {
            block()
        } finally {
            field.set(RepoUpdateConfigStore, previous)
        }
    }
}
