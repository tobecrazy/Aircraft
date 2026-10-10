package com.young.developtools.gui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Moved from the app's AboutMeActivityTest: TorchHero lives in FlashlightActivity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlashlightTorchHeroTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FlashlightActivity>()

    @Test
    fun `flashlight torch hero click toggles flashlight like switch`() {
        val toggledValues = mutableListOf<Boolean>()

        composeRule.activity.setContent {
            var torchOn by remember { mutableStateOf(false) }

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
}
