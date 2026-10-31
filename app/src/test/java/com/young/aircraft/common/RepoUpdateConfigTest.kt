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

    /** Stands in for the URL-dependent HTTP response bodies in tests. */
    private fun repoJson(minimum: String, latest: String): String =
        """{"minimum_version":"$minimum","latest_version":"$latest","update_url":"https://example.com/app.apk"}"""

    @Test
    fun `stale CDN copy does not preempt the fresh raw fallback`() {
        val requests = mutableListOf<String>()
        val applied = RepoUpdateConfigStore.fetchFirstSuccess { url ->
            requests += url
            when (url) {
                REPO_UPDATE_JSON_JSDELIVR_URL -> parseRepoUpdateConfig(repoJson("1.5.6", "1.5.6"))
                REPO_UPDATE_JSON_RAW_URL -> parseRepoUpdateConfig(repoJson("1.5.6", "1.5.7"))
                else -> null
            }
        }

        // Both sources must be attempted before the freshest copy is chosen.
        assertEquals(
            listOf(REPO_UPDATE_JSON_JSDELIVR_URL, REPO_UPDATE_JSON_RAW_URL),
            requests
        )
        assertEquals("1.5.7", applied?.latestVersion)
    }

    @Test
    fun `fresh CDN copy wins over a stale raw one`() {
        val applied = RepoUpdateConfigStore.fetchFirstSuccess { url ->
            when (url) {
                REPO_UPDATE_JSON_JSDELIVR_URL -> parseRepoUpdateConfig(repoJson("1.5.6", "1.5.7"))
                REPO_UPDATE_JSON_RAW_URL -> parseRepoUpdateConfig(repoJson("1.5.6", "1.5.6"))
                else -> null
            }
        }

        assertEquals("1.5.7", applied?.latestVersion)
    }

    @Test
    fun `failed CDN fetch falls back to the raw one`() {
        val applied = RepoUpdateConfigStore.fetchFirstSuccess { url ->
            if (url == REPO_UPDATE_JSON_JSDELIVR_URL) error("CDN unreachable")
            parseRepoUpdateConfig(repoJson("1.5.6", "1.5.7"))
        }

        assertEquals("1.5.7", applied?.latestVersion)
    }

    @Test
    fun `both sources failing yields no config`() {
        val applied = RepoUpdateConfigStore.fetchFirstSuccess { url ->
            if (url == REPO_UPDATE_JSON_JSDELIVR_URL || url == REPO_UPDATE_JSON_RAW_URL) {
                error("offline")
            }
            throw AssertionError("unexpected url $url")
        }

        assertNull(applied)
    }

    @Test
    fun `selects the copy with the highest latest version`() {
        val stale = RepoUpdateConfig(
            minimumVersion = "1.5.6",
            latestVersion = "1.5.6",
            updateUrl = "https://example.com/V1.5.6"
        )
        val fresh = RepoUpdateConfig(
            minimumVersion = "1.5.6",
            latestVersion = "1.5.7",
            updateUrl = "https://example.com/V1.5.7"
        )

        assertEquals(fresh, selectFreshestRepoUpdateConfig(listOf(stale, fresh)))
        assertEquals(fresh, selectFreshestRepoUpdateConfig(listOf(fresh, stale)))
        assertEquals(fresh, selectFreshestRepoUpdateConfig(listOf(fresh)))
    }

    @Test
    fun `unparsable latest version loses against a parsable one`() {
        val unparsable = RepoUpdateConfig(latestVersion = "not-a-version")
        val fresh = RepoUpdateConfig(latestVersion = "1.5.7")

        assertEquals(fresh, selectFreshestRepoUpdateConfig(listOf(unparsable, fresh)))
        assertEquals(fresh, selectFreshestRepoUpdateConfig(listOf(fresh, unparsable)))
        // Both unparsable (blank included): the first fetched copy wins.
        assertEquals(
            unparsable,
            selectFreshestRepoUpdateConfig(listOf(unparsable, RepoUpdateConfig()))
        )
    }

    @Test
    fun `selecting from an empty source list returns null`() {
        assertNull(selectFreshestRepoUpdateConfig(emptyList()))
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
