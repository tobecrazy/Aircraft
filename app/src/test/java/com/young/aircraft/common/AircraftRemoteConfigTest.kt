package com.young.aircraft.common

import android.app.Application
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AircraftRemoteConfigTest {

    @Test
    fun `parses token JSON fields and offset timestamps`() {
        val config = parseRemoteTokenConfig(
            """{"id":202610,"name":"tokenName","createdAt":"2026-10-05T14:31:11.360+00:00","expiringAt":"2029-10-05T14:31:11.360+00:00","enable":true}"""
        )

        assertEquals(202610L, config?.id)
        assertEquals("tokenName", config?.name)
        assertEquals(Instant.parse("2026-10-05T14:31:11.360Z"), config?.createdAt)
        assertEquals(Instant.parse("2029-10-05T14:31:11.360Z"), config?.expiringAt)
        assertTrue(config?.enable == true)
    }

    @Test
    fun `crashlytics collection stays enabled without a fetched remote value`() {
        val config = parseRemoteTokenConfig(
            """{"id":0,"name":"","createdAt":"1970-01-01T00:00:00Z","expiringAt":"1970-01-01T00:00:00Z","enable":false}"""
        )

        assertTrue(crashlyticsCollectionEnabled(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, config))
        assertTrue(crashlyticsCollectionEnabled(FirebaseRemoteConfig.VALUE_SOURCE_STATIC, null))
        assertTrue(crashlyticsCollectionEnabled(99, config))
    }

    @Test
    fun `remote value is trusted to enable or disable crashlytics collection`() {
        val enabledConfig = parseRemoteTokenConfig(
            """{"id":1,"name":"token","createdAt":"2026-10-05T14:31:11Z","expiringAt":"2029-10-05T14:31:11Z","enable":true}"""
        )
        val disabledConfig = parseRemoteTokenConfig(
            """{"id":1,"name":"token","createdAt":"2026-10-05T14:31:11Z","expiringAt":"2029-10-05T14:31:11Z","enable":false}"""
        )

        assertTrue(crashlyticsCollectionEnabled(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, enabledConfig))
        assertFalse(crashlyticsCollectionEnabled(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, disabledConfig))
    }

    @Test
    fun `unparsable remote value fails open for crashlytics collection`() {
        // A console-side typo is not an explicit revoke, so it keeps collection
        // on instead of silently disabling crash reporting.
        assertNull(parseRemoteTokenConfig("""{"id":1,"enable":false}"""))
        assertTrue(crashlyticsCollectionEnabled(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, null))
    }

    @Test
    fun `invalid JSON or missing required values returns null`() {
        assertNull(parseRemoteTokenConfig("not-json"))
        assertNull(parseRemoteTokenConfig("""{"id":1,"name":"token"}"""))
    }

    @Test
    fun `accepts app icon variants one through five`() {
        (1..5).forEach { variant ->
            assertEquals(variant, parseAppIconVariant(variant.toString()))
        }
        assertEquals(3, parseAppIconVariant(" 3 "))
    }

    @Test
    fun `invalid app icon values use default variant one`() {
        listOf("", "abc", "1.5", "0", "6", "-1").forEach { value ->
            assertEquals(1, parseAppIconVariant(value))
        }
    }

    @Test
    fun `parses optional welcome fields from token JSON`() {
        val config = parseRemoteTokenConfig(
            """{"id":7,"name":"tokenName","createdAt":"2026-10-05T14:31:11Z","expiringAt":"2029-10-05T14:31:11Z","enable":true,"welcomeMessage":"{{welcome_message}}","showWelcome":true}"""
        )

        assertEquals("{{welcome_message}}", config?.welcomeMessage)
        assertTrue(config?.showWelcome == true)
    }

    @Test
    fun `welcome placeholder and blank values fall back to the localized message`() {
        assertEquals("localized", resolveWelcomeMessage("", "localized"))
        assertEquals("localized", resolveWelcomeMessage("   ", "localized"))
        assertEquals("localized", resolveWelcomeMessage("{{welcome_message}}", "localized"))
        assertEquals("localized", resolveWelcomeMessage(" {{welcome_message}} ", "localized"))
        assertEquals("Remote text", resolveWelcomeMessage("Remote text", "localized"))
    }

    @Test
    fun `welcome placeholder matches case-insensitively and ignores invisible characters`() {
        assertEquals("localized", resolveWelcomeMessage("{{WELCOME_MESSAGE}}", "localized"))
        assertEquals("localized", resolveWelcomeMessage("{{Welcome_Message}}", "localized"))
        assertEquals(
            "localized",
            resolveWelcomeMessage("\u00A0{{welcome_message}}\u00A0", "localized")
        )
        assertEquals(
            "localized",
            resolveWelcomeMessage("\u00A0{{welcome_message}}\u200B", "localized")
        )
        assertEquals(
            "localized",
            resolveWelcomeMessage("\uFEFF{{welcome_message}}", "localized")
        )
    }

    @Test
    fun `welcome placeholder embedded in remote text is substituted`() {
        assertEquals(
            "Aircraft — localized — enjoy!",
            resolveWelcomeMessage("Aircraft — {{welcome_message}} — enjoy!", "localized")
        )
        assertEquals(
            "localized twice: localized",
            resolveWelcomeMessage("{{welcome_message}} twice: {{WELCOME_MESSAGE}}", "localized")
        )
    }

    @Test
    fun `compares numeric version components instead of strings`() {
        assertTrue(compareAppVersions("1.10.0", "1.2.0")!! > 0)
        assertTrue(compareAppVersions("1.3.5", "1.3.6")!! < 0)
        assertEquals(0, compareAppVersions("1.3.6", "1.3.6.0"))
        assertNull(compareAppVersions("1.3.x", "1.3.6"))
    }

    @Test
    fun `offers update when current version is above minimum and below latest`() {
        assertTrue(hasOptionalAppUpdate("1.3.6", "1.3.6", "1.3.7"))
    }

    @Test
    fun `does not offer optional update below the enforced minimum`() {
        assertFalse(hasOptionalAppUpdate("1.3.5", "1.3.6", "1.3.7"))
    }

    @Test
    fun `does not offer optional update when current version meets latest`() {
        assertFalse(hasOptionalAppUpdate("1.3.7", "1.3.6", "1.3.7"))
    }

    @Test
    fun `does not offer optional update when latest version is blank or invalid`() {
        assertFalse(hasOptionalAppUpdate("1.3.6", "1.3.6", ""))
        assertFalse(hasOptionalAppUpdate("1.3.6", "1.3.6", "1.x"))
    }
}
