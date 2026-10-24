package com.young.aircraft.common

import android.app.Application
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
class RepoUpdateConfigTest {

    @Test
    fun `parses all version fields`() {
        val config = parseRepoUpdateConfig(
            """{"minimum_version":"1.3.6","latest_version":"1.4.2","update_url":"https://example.com/app.apk"}"""
        )

        assertEquals("1.3.6", config?.minimumVersion)
        assertEquals("1.4.2", config?.latestVersion)
        assertEquals("https://example.com/app.apk", config?.updateUrl)
    }

    @Test
    fun `parses icon and welcome fallback fields`() {
        val config = parseRepoUpdateConfig(
            """{"minimum_version":"1.4.1","latest_version":"1.4.5","update_url":"https://example.com/app.apk","app_icon":3,"welcomeMessage":"hello","showWelcome":true}"""
        )

        assertEquals("3", config?.appIcon)
        assertEquals("hello", config?.welcomeMessage)
        assertTrue(config?.showWelcome == true)
    }

    @Test
    fun `accepts Firebase-canonical AppIcon key`() {
        val config = parseRepoUpdateConfig("""{"AppIcon":"4"}""")

        assertEquals("4", config?.appIcon)
    }

    @Test
    fun `missing icon and welcome fields default to blank and false`() {
        val config = parseRepoUpdateConfig("""{"latest_version":"1.4.2"}""")

        assertEquals("", config?.appIcon)
        assertEquals("", config?.welcomeMessage)
        assertFalse(config?.showWelcome == true)
    }

    @Test
    fun `parses localized welcome placeholder`() {
        val config = parseRepoUpdateConfig(
            """{"latest_version":"1.5.5","welcomeMessage":"{{welcome_message}}","showWelcome":true}"""
        )

        assertEquals("{{welcome_message}}", config?.welcomeMessage)
        assertTrue(config?.showWelcome == true)
        assertEquals(
            "Aircraft is localized",
            resolveWelcomeMessage(config?.welcomeMessage.orEmpty(), "Aircraft is localized")
        )
    }

    @Test
    fun `invalid JSON returns null`() {
        assertNull(parseRepoUpdateConfig("not-json"))
    }

    @Test
    fun `repo JSON urls point at app-update json on main`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/tobecrazy/Aircraft@main/app-update.json",
            REPO_UPDATE_JSON_JSDELIVR_URL
        )
        assertEquals(
            "https://raw.githubusercontent.com/tobecrazy/Aircraft/main/app-update.json",
            REPO_UPDATE_JSON_RAW_URL
        )
    }
}
