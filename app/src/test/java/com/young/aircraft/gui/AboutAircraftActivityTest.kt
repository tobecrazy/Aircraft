package com.young.aircraft.gui

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.core.app.ActivityScenario
import com.young.aircraft.R
import com.young.aircraft.data.ImageDetailsIntentContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h920dp")
class AboutAircraftActivityTest {

    private fun root(activity: AboutAircraftActivity): SemanticsNode =
        activity.window.decorView.findSemanticsOwner()!!.rootSemanticsNode

    @Test
    fun `activity launches and displays version info`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val texts = findAllNodes(root(activity)).mapNotNull { it.displayText() }

                assertTrue(texts.any { it.startsWith("v") })
                assertTrue(activity.getString(R.string.about_banner_summary) in texts)
            }
        }
    }

    @Test
    fun `clicking primary github button starts action view intent`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("github_cta_primary"))

                val intent: Intent = shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(activity.getString(R.string.about_me_project_repo_url), intent.dataString)
            }
        }
    }

    @Test
    fun `clicking source card starts action view intent`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("github_source_card"))

                val intent: Intent = shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(activity.getString(R.string.about_me_project_repo_url), intent.dataString)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("btn_back"))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `clicking project image launches ShowImageDetailsActivity`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("project_image"))

                val intent: Intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(ShowImageDetailsActivity::class.java.name, intent.component?.className)

                val name = intent.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME)
                val description = intent.getStringExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION)
                val sourceType = intent.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE)
                val url = intent.getStringExtra(ImageDetailsIntentContract.EXTRA_URL)

                assertNotNull(name)
                assertNotNull(description)
                assertEquals(ImageDetailsIntentContract.SOURCE_NETWORK, sourceType)
                assertNotNull(url)
            }
        }
    }

    @Test
    fun `project image intent contains correct banner details`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(root(activity).clickOnTag("project_image"))

                val intent: Intent = shadowOf(activity).nextStartedActivity
                assertEquals(activity.getString(R.string.about_aircraft_title),
                    intent.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME))
                assertEquals(activity.getString(R.string.about_banner_summary),
                    intent.getStringExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION))
                assertEquals(ImageDetailsIntentContract.SOURCE_NETWORK,
                    intent.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE))
                assertNull(intent.getIntExtra(ImageDetailsIntentContract.EXTRA_RES_ID, -1).takeIf { it != -1 })
            }
        }
    }
}
