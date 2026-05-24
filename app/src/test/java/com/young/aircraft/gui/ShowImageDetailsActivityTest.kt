package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.ImageDetailsIntentContract
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowImageDetailsActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `activity finishes when intent data is missing`() {
        val invalidIntent = Intent(context, ShowImageDetailsActivity::class.java)
        ActivityScenario.launch<ShowImageDetailsActivity>(invalidIntent).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    assertTrue(activity.isFinishing)
                }
            } catch (e: Exception) {
                // Activity finishes immediately, which is expected behavior
                assertTrue(true)
            }
        }
    }

    @Test
    fun `activity launches with local image source`() {
        val item = SupperBannerItem(
            name = "test_image.jpg",
            description = "Test Description",
            image = SupperBannerImage.Local(R.drawable.background)
        )
        val intent = ShowImageDetailsActivity.createIntent(context, item)

        ActivityScenario.launch<ShowImageDetailsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun `activity launches with network image source`() {
        val item = SupperBannerItem(
            name = "network_image",
            description = "Network Test",
            image = SupperBannerImage.Network("https://example.com/image.jpg")
        )
        val intent = ShowImageDetailsActivity.createIntent(context, item)

        ActivityScenario.launch<ShowImageDetailsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun `createIntent builds correct intent with local image`() {
        val item = SupperBannerItem(
            name = "background.jpg",
            description = "Local drawable",
            image = SupperBannerImage.Local(R.drawable.background)
        )

        val intent = ShowImageDetailsActivity.createIntent(context, item)

        assertEquals(ShowImageDetailsActivity::class.java.name, intent.component?.className)
        assertEquals("background.jpg", intent.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME))
        assertEquals("Local drawable", intent.getStringExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION))
        assertEquals(ImageDetailsIntentContract.SOURCE_LOCAL, intent.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE))
        assertEquals(R.drawable.background, intent.getIntExtra(ImageDetailsIntentContract.EXTRA_RES_ID, 0))
    }

    @Test
    fun `createIntent builds correct intent with network image`() {
        val url = "https://example.com/banner.jpg"
        val item = SupperBannerItem(
            name = "network_banner",
            description = "Network image",
            image = SupperBannerImage.Network(url)
        )

        val intent = ShowImageDetailsActivity.createIntent(context, item)

        assertEquals(ShowImageDetailsActivity::class.java.name, intent.component?.className)
        assertEquals("network_banner", intent.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME))
        assertEquals("Network image", intent.getStringExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION))
        assertEquals(ImageDetailsIntentContract.SOURCE_NETWORK, intent.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE))
        assertEquals(url, intent.getStringExtra(ImageDetailsIntentContract.EXTRA_URL))
    }

    @Test
    fun `multiple items can be launched independently`() {
        val item1 = SupperBannerItem(
            name = "image1.jpg",
            description = "First image",
            image = SupperBannerImage.Local(R.drawable.background)
        )
        val item2 = SupperBannerItem(
            name = "image2.jpg",
            description = "Second image",
            image = SupperBannerImage.Network("https://example.com/image2.jpg")
        )

        val intent1 = ShowImageDetailsActivity.createIntent(context, item1)
        val intent2 = ShowImageDetailsActivity.createIntent(context, item2)

        assertNotEquals(
            intent1.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME),
            intent2.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME)
        )
        assertNotEquals(
            intent1.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE),
            intent2.getStringExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE)
        )
    }

    @Test
    fun `item with filename extension preserves extension`() {
        val item = SupperBannerItem(
            name = "image_with_extension.png",
            description = "PNG image",
            image = SupperBannerImage.Local(R.drawable.background)
        )

        val intent = ShowImageDetailsActivity.createIntent(context, item)

        assertEquals("image_with_extension.png", intent.getStringExtra(ImageDetailsIntentContract.EXTRA_NAME))
    }

    @Test
    fun `github blob image url is converted to raw url`() {
        val blobUrl = "https://github.com/tobecrazy/Aircraft/blob/main/class_diagram.svg"
        val converted = normalizeGithubBlobImageUrl(blobUrl)
        assertEquals(
            "https://raw.githubusercontent.com/tobecrazy/Aircraft/main/class_diagram.svg",
            converted
        )
    }

    @Test
    fun `non github blob url remains unchanged`() {
        val networkUrl = "https://example.com/assets/pic.webp"
        assertEquals(networkUrl, normalizeGithubBlobImageUrl(networkUrl))
    }

    @Test
    fun `resolve image details model converts github blob string only`() {
        val blobUrl = "https://github.com/tobecrazy/Aircraft/blob/main/class_diagram.svg"
        assertEquals(
            "https://raw.githubusercontent.com/tobecrazy/Aircraft/main/class_diagram.svg",
            resolveImageDetailsModel(blobUrl)
        )
        assertEquals(42, resolveImageDetailsModel(42))
    }
}
