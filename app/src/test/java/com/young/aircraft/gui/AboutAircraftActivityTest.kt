package com.young.aircraft.gui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutAircraftActivityTest {

    @Test
    fun `activity launches and displays version info`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvVersion = activity.findViewById<TextView>(R.id.tv_version_badge)
                val tvSummary = activity.findViewById<TextView>(R.id.tv_project_summary)
                assertNotNull(tvVersion.text)
                assertTrue(tvVersion.text.startsWith("v"))
                assertEquals(
                    activity.getString(R.string.about_banner_summary),
                    tvSummary.text.toString()
                )
            }
        }
    }

    @Test
    fun `clicking primary github button starts action view intent`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val githubLink = activity.findViewById<View>(R.id.btn_open_github_primary)
                githubLink.performClick()

                val shadowActivity = shadowOf(activity)
                val intent = shadowActivity.nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(activity.getString(R.string.about_me_project_repo_url), intent.dataString)
            }
        }
    }

    @Test
    fun `clicking source card starts action view intent`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val githubLink = activity.findViewById<View>(R.id.ll_github_link)
                githubLink.performClick()

                val shadowActivity = shadowOf(activity)
                val intent = shadowActivity.nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(activity.getString(R.string.about_me_project_repo_url), intent.dataString)
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
