package com.young.aircraft.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageDetailsIntentContractTest {

    @Test
    fun `fromIntent creates local banner details`() {
        val intent = Intent()
            .putExtra(ImageDetailsIntentContract.EXTRA_NAME, "background.jpg")
            .putExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION, "Local drawable")
            .putExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE, ImageDetailsIntentContract.SOURCE_LOCAL)
            .putExtra(ImageDetailsIntentContract.EXTRA_RES_ID, 42)

        val details = ImageDetailsIntentContract.fromIntent(intent)

        assertEquals("background.jpg", details?.name)
        assertEquals("Local drawable", details?.description)
        assertEquals(ImageDetailsSource.Local(42), details?.source)
    }

    @Test
    fun `fromIntent creates network banner details`() {
        val intent = Intent()
            .putExtra(ImageDetailsIntentContract.EXTRA_NAME, "network_background")
            .putExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION, "Direct network image")
            .putExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE, ImageDetailsIntentContract.SOURCE_NETWORK)
            .putExtra(ImageDetailsIntentContract.EXTRA_URL, "https://example.com/image.jpg")

        val details = ImageDetailsIntentContract.fromIntent(intent)

        assertEquals("network_background", details?.name)
        assertEquals("Direct network image", details?.description)
        assertEquals(ImageDetailsSource.Network("https://example.com/image.jpg"), details?.source)
    }

    @Test
    fun `fromIntent returns null for missing required fields`() {
        assertNull(ImageDetailsIntentContract.fromIntent(Intent()))
    }
}
