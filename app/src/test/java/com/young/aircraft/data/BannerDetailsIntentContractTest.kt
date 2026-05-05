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
class BannerDetailsIntentContractTest {

    @Test
    fun `fromIntent creates local banner details`() {
        val intent = Intent()
            .putExtra(BannerDetailsIntentContract.EXTRA_NAME, "background.jpg")
            .putExtra(BannerDetailsIntentContract.EXTRA_DESCRIPTION, "Local drawable")
            .putExtra(BannerDetailsIntentContract.EXTRA_SOURCE_TYPE, BannerDetailsIntentContract.SOURCE_LOCAL)
            .putExtra(BannerDetailsIntentContract.EXTRA_RES_ID, 42)

        val details = BannerDetailsIntentContract.fromIntent(intent)

        assertEquals("background.jpg", details?.name)
        assertEquals("Local drawable", details?.description)
        assertEquals(BannerDetailsSource.Local(42), details?.source)
    }

    @Test
    fun `fromIntent creates network banner details`() {
        val intent = Intent()
            .putExtra(BannerDetailsIntentContract.EXTRA_NAME, "network_background")
            .putExtra(BannerDetailsIntentContract.EXTRA_DESCRIPTION, "Direct network image")
            .putExtra(BannerDetailsIntentContract.EXTRA_SOURCE_TYPE, BannerDetailsIntentContract.SOURCE_NETWORK)
            .putExtra(BannerDetailsIntentContract.EXTRA_URL, "https://example.com/image.jpg")

        val details = BannerDetailsIntentContract.fromIntent(intent)

        assertEquals("network_background", details?.name)
        assertEquals("Direct network image", details?.description)
        assertEquals(BannerDetailsSource.Network("https://example.com/image.jpg"), details?.source)
    }

    @Test
    fun `fromIntent returns null for missing required fields`() {
        assertNull(BannerDetailsIntentContract.fromIntent(Intent()))
    }
}
