package com.young.aircraft.gui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.LocaleListCompat
import com.young.aircraft.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h2000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingsActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSettingsActivity>()

    @After
    fun tearDown() {
        // The delegate is process-global static state; reset so tests stay order-independent.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    private fun text(res: Int) = composeRule.activity.getString(res)

    @Test
    fun `all language rows render with follow system selected by default`() {
        listOf(
            R.string.language_follow_system,
            R.string.language_simplified_chinese,
            R.string.language_traditional_taiwan,
            R.string.language_traditional_hongkong,
            R.string.language_english
        ).forEach { res ->
            composeRule.onNodeWithText(text(res)).performScrollTo().assertExists()
        }
        composeRule.onNodeWithText(text(R.string.language_follow_system)).assertIsSelected()
    }

    @Test
    fun `selecting a row updates selection before saving`() {
        composeRule.onNodeWithText(text(R.string.language_traditional_hongkong))
            .performScrollTo().performClick().assertIsSelected()
    }

    @Test
    fun `saving a language applies application locales and finishes`() {
        composeRule.onNodeWithText(text(R.string.language_traditional_taiwan))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.language_save)).performClick()
        composeRule.waitForIdle()

        assertEquals("zh-TW", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `saving follow system clears previously applied locale`() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))

        composeRule.onNodeWithText(text(R.string.language_follow_system)).performClick()
        composeRule.onNodeWithText(text(R.string.language_save)).performClick()
        composeRule.waitForIdle()

        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `back discards unsaved selection`() {
        composeRule.onNodeWithText(text(R.string.language_english))
            .performScrollTo().performClick().assertIsSelected()

        composeRule.onNodeWithContentDescription(text(R.string.history_cancel)).performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.activity.isFinishing)
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }
}
