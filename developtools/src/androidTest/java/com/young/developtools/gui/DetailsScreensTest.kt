package com.young.developtools.gui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.young.developtools.R
import com.young.developtools.data.BannerDetailsIntentContract
import com.young.developtools.data.ImageDetailsIntentContract
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected screen tests for the two detail viewers. Both finish themselves when
 * their intent payload is missing, so the tests supply a local drawable through
 * the same contract the production callers use.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DetailsScreensTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bannerDetailsScreenRenders() {
        val intent = Intent(targetContext, BannerDetailsActivity::class.java).apply {
            putExtra(BannerDetailsIntentContract.EXTRA_NAME, "test_banner")
            putExtra(BannerDetailsIntentContract.EXTRA_DESCRIPTION, "Instrumented test banner")
            putExtra(BannerDetailsIntentContract.EXTRA_SOURCE_TYPE, BannerDetailsIntentContract.SOURCE_LOCAL)
            putExtra(BannerDetailsIntentContract.EXTRA_RES_ID, R.drawable.background_1)
        }
        withScreen(intent, BannerDetailsActivity::class.java) { screen ->
            assertTrue("banner details title", screen.waitForText(R.string.banner_details_title))
        }
    }

    @Test
    fun imageDetailsScreenRenders() {
        val intent = Intent(targetContext, ShowImageDetailsActivity::class.java).apply {
            putExtra(ImageDetailsIntentContract.EXTRA_NAME, "test_image")
            putExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION, "Instrumented test image")
            putExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE, ImageDetailsIntentContract.SOURCE_LOCAL)
            putExtra(ImageDetailsIntentContract.EXTRA_RES_ID, R.drawable.background_1)
        }
        withScreen(intent, ShowImageDetailsActivity::class.java) { screen ->
            assertTrue("image details title", screen.waitForText(R.string.banner_details_title))
        }
    }
}
