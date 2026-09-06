package com.jamesmoran.adventurepad

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.junit.Rule
import org.junit.Test

class LauncherSaveActionsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldAtlantisContextMenuPublishesEnabledResumeAndLoad() {
        openRefreshedAtlantisContextMenu()

        composeRule.onNodeWithText("Resume Game").assertIsEnabled()
        composeRule.onNodeWithText("Load Game").assertIsEnabled()
    }

    @Test
    fun coldAtlantisResumeUsesLauncherAction() {
        openRefreshedAtlantisContextMenu()

        composeRule.onNodeWithText("Resume Game").assertIsEnabled().performClick()
        Thread.sleep(3000)
    }

    @Test
    fun coldAtlantisLoadUsesLauncherAction() {
        openRefreshedAtlantisContextMenu()

        composeRule.onNodeWithText("Load Game").assertIsEnabled().performClick()
        Thread.sleep(3000)
    }

    @Test
    fun coldSkyContextMenuKeepsResumeUnavailable() {
        openRefreshedContextMenu("sky")

        composeRule.onNodeWithText("Resume Game").assertIsNotEnabled()
        composeRule.onNodeWithText("No valid saves were found.").fetchSemanticsNode()
    }

    @Test
    fun normalAtlantisCardActivationStillUsesPlay() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("game-card-atlantis").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("game-card-atlantis").performClick()
        Thread.sleep(3000)
    }

    private fun openRefreshedAtlantisContextMenu() {
        openRefreshedContextMenu("atlantis")
    }

    private fun openRefreshedContextMenu(targetId: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("game-card-$targetId").fetchSemanticsNodes().isNotEmpty()
        }
        // The cold native scan normally takes under a second on Thor. Re-open the menu
        // after publication so its selected-target snapshot contains the refreshed state.
        Thread.sleep(2000)
        composeRule.onNodeWithTag("game-card-$targetId").performMouseInput { rightClick() }
        composeRule.waitForIdle()
    }
}
