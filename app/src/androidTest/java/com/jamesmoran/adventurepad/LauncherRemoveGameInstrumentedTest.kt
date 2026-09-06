package com.jamesmoran.adventurepad

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.junit.Rule
import org.junit.Test

class LauncherRemoveGameInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun removeGameRequiresExplicitConfirmationAndCancelPreservesLibrary() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("game-card-atlantis").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("game-card-atlantis").performMouseInput { rightClick() }
        composeRule.onNodeWithText("Remove Game").performClick()

        composeRule.onNodeWithTag("remove-game-dialog").assertExists()
        composeRule.onNodeWithText(
            "This removes the game from AdventurePad and ScummVM. " +
                "Your game files and saved games will not be deleted.",
        ).assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("game-card-atlantis").assertExists()
    }
}
