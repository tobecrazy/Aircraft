package com.young.aircraft.gui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutMeActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<AboutMeActivity>()

    @Test
    fun `screen renders localized title sections and repo url`() {
        val activity = composeRule.activity

        composeRule.waitForIdle()

        composeRule.onNodeWithText(activity.getString(R.string.about_me_title))
            .assertTextEquals(activity.getString(R.string.about_me_title))
        composeRule.onNodeWithText(activity.getString(R.string.about_me_developer_section_title))
            .assertTextEquals(activity.getString(R.string.about_me_developer_section_title))
        composeRule.onNodeWithText(activity.getString(R.string.about_me_project_section_title))
            .assertTextEquals(activity.getString(R.string.about_me_project_section_title))

        val repoNodes = composeRule.onAllNodesWithText(
            activity.getString(R.string.about_me_project_repo_url),
            substring = true
        ).fetchSemanticsNodes()
        assertTrue(repoNodes.isNotEmpty())
    }

    @Test
    fun `back arrow finishes activity`() {
        composeRule.onNodeWithText("\u25C0").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `project copy is localized for english and chinese`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        assertLocalizedProjectCopy(
            context = appContext.localizedFor(Locale.ENGLISH),
            expectedRepoLine = "GitHub: https://github.com/tobecrazy/Aircraft"
        )
        assertLocalizedProjectCopy(
            context = appContext.localizedFor(Locale.SIMPLIFIED_CHINESE),
            expectedRepoLine = "GitHub：https://github.com/tobecrazy/Aircraft"
        )
    }

    private fun assertLocalizedProjectCopy(context: Context, expectedRepoLine: String) {
        val repoLine = context.getString(
            R.string.about_me_project_repo_line,
            context.getString(R.string.about_github_label),
            context.getString(R.string.about_me_project_repo_url)
        )
        val projectContent = context.getString(R.string.about_me_project_content, repoLine)

        assertEquals(expectedRepoLine, repoLine)
        assertTrue(projectContent.contains(repoLine))
        assertTrue(context.getString(R.string.about_me_content).isNotBlank())
    }

    private fun Context.localizedFor(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration)
    }
}
