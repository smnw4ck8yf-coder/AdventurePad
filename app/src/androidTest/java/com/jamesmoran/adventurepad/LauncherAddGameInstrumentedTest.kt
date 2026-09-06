package com.jamesmoran.adventurepad

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherAddGameInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherExposesAccessibleAddGameTouchTarget() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("launcher-add-game").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("launcher-add-game")
            .assertContentDescriptionEquals("Add Game")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun addGameSharesTheExpandedSortControlGroup() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("launcher-edit-order").fetchSemanticsNodes().isNotEmpty()
        }

        val sort = composeRule.onNodeWithTag("launcher-sort").fetchSemanticsNode().boundsInRoot
        val edit = composeRule.onNodeWithTag("launcher-edit-order").fetchSemanticsNode().boundsInRoot
        val add = composeRule.onNodeWithTag("launcher-add-game-visible").fetchSemanticsNode().boundsInRoot

        assertTrue(sort.right < edit.left)
        assertTrue(edit.right < add.left)
        assertEquals(sort.height, edit.height, 0.5f)
        assertEquals(edit.height, add.height, 0.5f)
    }
}
