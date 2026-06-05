package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

        val titleNodes = composeRule.onAllNodesWithText(activity.getString(R.string.about_me_title))
            .fetchSemanticsNodes()
        assertTrue(titleNodes.isNotEmpty())
        composeRule.onNodeWithTag("about_me_list")
            .performScrollToNode(hasText(activity.getString(R.string.about_me_developer_section_title)))
        composeRule.onNodeWithText(activity.getString(R.string.about_me_developer_section_title))
            .assertTextEquals(activity.getString(R.string.about_me_developer_section_title))
        composeRule.onNodeWithTag("about_me_list")
            .performScrollToNode(hasText(activity.getString(R.string.about_me_project_section_title)))
        composeRule.onNodeWithText(activity.getString(R.string.about_me_project_section_title))
            .assertTextEquals(activity.getString(R.string.about_me_project_section_title))

        val repoNodes = composeRule.onAllNodesWithText(activity.getString(R.string.about_me_project_repo_url))
            .fetchSemanticsNodes()
        assertTrue(repoNodes.isNotEmpty())
    }

    @Test
    fun `back arrow finishes activity`() {
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.history_back)
        ).performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `github action launches browser intent`() {
        val activity = composeRule.activity

        composeRule.onNodeWithTag("about_me_open_repo").performClick()
        composeRule.waitForIdle()

        val nextIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, nextIntent.action)
        assertEquals(
            activity.getString(R.string.about_me_project_repo_url),
            nextIntent.dataString
        )
    }

    @Test
    fun `flashlight torch hero click toggles flashlight like switch`() {
        val toggledValues = mutableListOf<Boolean>()

        composeRule.activity.setContent {
            var torchOn by mutableStateOf(false)

            MaterialTheme {
                TorchHero(
                    isOn = torchOn,
                    isSosMode = false,
                    enabled = true,
                    onToggleFlashlight = {
                        torchOn = !torchOn
                        toggledValues += torchOn
                    }
                )
            }
        }

        composeRule.onNodeWithTag(FLASHLIGHT_TORCH_HERO_TAG).performClick()
        composeRule.onNodeWithTag(FLASHLIGHT_TORCH_HERO_TAG).performClick()

        assertEquals(listOf(true, false), toggledValues)
    }

    @Test
    fun `flashlight torch hero is disabled when controls are disabled`() {
        composeRule.activity.setContent {
            MaterialTheme {
                TorchHero(
                    isOn = false,
                    isSosMode = false,
                    enabled = false,
                    onToggleFlashlight = {}
                )
            }
        }

        composeRule.onNodeWithTag(FLASHLIGHT_TORCH_HERO_TAG).assertIsNotEnabled()
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
