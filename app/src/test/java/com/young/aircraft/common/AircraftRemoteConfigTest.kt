package com.young.aircraft.common

import android.app.Application
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
