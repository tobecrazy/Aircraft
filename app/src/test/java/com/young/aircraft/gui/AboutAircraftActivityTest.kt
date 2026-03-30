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
                assertNotNull(tvVersion.text)
                assertTrue(tvVersion.text.startsWith("v"))
            }
        }
    }

    @Test
    fun `clicking github link starts action view intent`() {
        ActivityScenario.launch(AboutAircraftActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val githubLink = activity.findViewById<View>(R.id.ll_github_link)
                githubLink.performClick()

                val shadowActivity = shadowOf(activity)
                val intent = shadowActivity.nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals("https://github.com/tobecrazy/Aircraft", intent.dataString)
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
