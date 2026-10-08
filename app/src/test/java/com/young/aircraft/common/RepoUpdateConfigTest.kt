package com.young.aircraft.common

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `missing fields default to blank`() {
        val config = parseRepoUpdateConfig("""{"latest_version":"1.4.2"}""")

        assertEquals("", config?.minimumVersion)
        assertEquals("1.4.2", config?.latestVersion)
        assertEquals("", config?.updateUrl)
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
