package com.young.aircraft.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PrivacyPolicyAssetTest {

    private fun asset(tag: String) =
        AircraftConstants.PrivacyPolicy.assetFor(Locale.forLanguageTag(tag))

    @Test
    fun `simplified chinese locales read the simplified policy`() {
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH, asset("zh"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH, asset("zh-CN"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH, asset("zh-Hans"))
    }

    @Test
    fun `taiwanese and hong kong locales read the traditional policy`() {
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH_HANT, asset("zh-TW"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH_HANT, asset("zh-HK"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH_HANT, asset("zh-Hant"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_ZH_HANT, asset("zh-Hant-TW"))
    }

    @Test
    fun `other languages read the english policy`() {
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_EN, asset("en"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_EN, asset("ja"))
        assertEquals(AircraftConstants.PrivacyPolicy.ASSET_EN, asset("fr-FR"))
    }

    @Test
    fun `asset urls round trip back to their file name`() {
        val prefix = AircraftConstants.PrivacyPolicy.ASSET_PREFIX
        AircraftConstants.PrivacyPolicy.ALL_ASSETS.forEach { file ->
            assertEquals(file, AircraftConstants.PrivacyPolicy.assetFromUrl("$prefix$file"))
        }
    }

    @Test
    fun `unknown urls fall back to english`() {
        assertEquals(
            AircraftConstants.PrivacyPolicy.ASSET_EN,
            AircraftConstants.PrivacyPolicy.assetFromUrl("file:///android_asset/nope.html")
        )
    }
}