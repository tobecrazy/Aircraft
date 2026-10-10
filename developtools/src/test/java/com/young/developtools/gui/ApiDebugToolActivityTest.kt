package com.young.developtools.gui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiDebugToolActivityTest {

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ApiDebugToolActivity>()

    @Test
    fun `send is disabled until the request validates`() {
        // Empty URL: gate closed.
        composeTestRule.onNodeWithTag("input_url").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_send").assertIsNotEnabled()

        // Valid URL: gate opens (default headers are valid, body is empty).
        composeTestRule.onNodeWithTag("input_url").performTextInput("https://example.com/api")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_send").assertIsEnabled()
    }

    @Test
    fun `history lives behind the entrance only`() {
        // Entrance button is reachable…
        composeTestRule.onNodeWithTag("btn_view_all_history")
            .performScrollTo()
            .assertIsDisplayed()
        // …while the old inline clear button is gone from this page.
        composeTestRule.onNodeWithTag("btn_clear_history").assertDoesNotExist()
    }
}
