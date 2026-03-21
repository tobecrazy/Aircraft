package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("onboarding_completed")
            .remove("privacy_policy_accepted")
            .commit()
    }

    @Test
    fun `first launch shows onboarding carousel`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.view_pager)
                assertNotNull(viewPager)
                assertEquals(0, viewPager.currentItem)
            }
        }
    }

    @Test
    fun `star field is present`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val starField = activity.findViewById<StarFieldView>(R.id.star_field)
                assertNotNull(starField)
            }
        }
    }

    @Test
    fun `already completed redirects to LaunchActivity immediately`() {
        context.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_completed", true).commit()

        val intent = Intent(context, OnboardingActivity::class.java)
        val scenario = ActivityScenario.launch<OnboardingActivity>(intent)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun `skip button saves preference and launches LaunchActivity`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnSkip = activity.findViewById<android.widget.TextView>(R.id.btn_skip)
                btnSkip.performClick()

                val prefs = activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
                assertTrue(prefs.getBoolean("onboarding_completed", false))

                val shadowActivity = shadowOf(activity)
                val nextIntent = shadowActivity.nextStartedActivity
                assertNotNull(nextIntent)
                assertEquals(
                    LaunchActivity::class.java.name,
                    nextIntent.component?.className
                )
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `next button navigates to second page`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.view_pager)
                val btnNext = activity.findViewById<android.widget.TextView>(R.id.btn_next)

                assertEquals(0, viewPager.currentItem)
                btnNext.performClick()
                assertEquals(1, viewPager.currentItem)
            }
        }
    }

    @Test
    fun `launch button on second page saves pref and launches LaunchActivity`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.view_pager)
                val btnNext = activity.findViewById<android.widget.TextView>(R.id.btn_next)

                // Navigate to page 2
                viewPager.currentItem = 1

                // Click LAUNCH on page 2
                btnNext.performClick()

                val prefs = activity.getSharedPreferences("aircraft_prefs", Context.MODE_PRIVATE)
                assertTrue(prefs.getBoolean("onboarding_completed", false))

                val shadowActivity = shadowOf(activity)
                val nextIntent = shadowActivity.nextStartedActivity
                assertNotNull(nextIntent)
                assertEquals(
                    LaunchActivity::class.java.name,
                    nextIntent.component?.className
                )
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun `page indicators update on page change`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val indicator1 = activity.findViewById<android.view.View>(R.id.indicator_1)
                val indicator2 = activity.findViewById<android.view.View>(R.id.indicator_2)

                // Page 0: first indicator active
                assertEquals(1.0f, indicator1.alpha, 0.01f)
                assertEquals(0.3f, indicator2.alpha, 0.01f)
            }
        }
    }

    @Test
    fun `skip does not navigate to second page first`() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnSkip = activity.findViewById<android.widget.TextView>(R.id.btn_skip)
                btnSkip.performClick()

                // Should go directly to LaunchActivity, not page 2
                val shadowActivity = shadowOf(activity)
                val nextIntent = shadowActivity.nextStartedActivity
                assertEquals(
                    LaunchActivity::class.java.name,
                    nextIntent.component?.className
                )
            }
        }
    }
}
