package com.jamesmoran.adventurepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import org.junit.Rule
import org.junit.Test

class ExternalLaunchersWizardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstTimeFlowStartsAtLauncherAndAdvancesOneStepAtATime() {
        composeRule.setContent {
            var page by remember { mutableStateOf(ExternalLaunchersPage.CHOOSE_LAUNCHER) }
            AdventurePadTheme {
                TestExternalLaunchersScreen(
                    page = page,
                    onConfirmLauncher = { page = ExternalLaunchersPage.CHOOSE_FOLDER },
                    onFolderNext = { page = ExternalLaunchersPage.REVIEW },
                )
            }
        }

        composeRule.onNodeWithText("Step 1 of 3  •  Which launcher do you use?").assertIsDisplayed()
        composeRule.onNodeWithText("Cocoon  •  Installed  •  Selected").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard-next").performClick()
        composeRule.onNodeWithText("Step 2 of 3  •  Where should AdventurePad prepare launcher files?")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("wizard-next").performClick()
        composeRule.onNodeWithText("Step 3 of 3  •  Ready to prepare your games?").assertIsDisplayed()
    }

    @Test
    fun skipRequiresAValidSavedFolder() {
        var permissionValid by mutableStateOf(false)
        composeRule.setContent {
            AdventurePadTheme {
                TestExternalLaunchersScreen(
                    page = ExternalLaunchersPage.CHOOSE_FOLDER,
                    folderPermissionValid = permissionValid,
                )
            }
        }

        composeRule.onNodeWithTag("wizard-skip").assertIsNotEnabled()
        composeRule.onNodeWithTag("wizard-next").assertIsNotEnabled()

        composeRule.runOnIdle { permissionValid = true }

        composeRule.onNodeWithTag("wizard-skip").assertIsEnabled()
        composeRule.onNodeWithTag("wizard-next").assertIsEnabled()
        composeRule.onNodeWithText("Scummvm").assertIsDisplayed()
    }

    @Test
    fun returningUserSeesReadyAndCanRunSetupAgainOrReadInstructions() {
        var page by mutableStateOf(ExternalLaunchersPage.READY)
        composeRule.setContent {
            AdventurePadTheme {
                TestExternalLaunchersScreen(
                    page = page,
                    onRunSetupAgain = { page = ExternalLaunchersPage.CHOOSE_LAUNCHER },
                    onShowInstructions = { page = ExternalLaunchersPage.SUCCESS },
                )
            }
        }

        composeRule.onNodeWithTag("external-launchers-page-ready").assertIsDisplayed()
        composeRule.onNodeWithTag("update-prepared-games").assertIsDisplayed()
        composeRule.onNodeWithText("Setup Instructions").performClick()
        composeRule.onNodeWithTag("external-launchers-page-success").assertIsDisplayed()

        composeRule.runOnIdle { page = ExternalLaunchersPage.READY }
        composeRule.onNodeWithTag("run-setup-again").performClick()
        composeRule.onNodeWithTag("external-launchers-page-launcher").assertIsDisplayed()
    }

    @Test
    fun gameConflictAllowsPreparationButPlatformConflictBlocksIt() {
        var currentPreview by mutableStateOf(preparationPlan(gameConflict = true))
        composeRule.setContent {
            AdventurePadTheme {
                TestExternalLaunchersScreen(
                    page = ExternalLaunchersPage.REVIEW,
                    preview = currentPreview,
                )
            }
        }

        composeRule.onNodeWithTag("preparation-conflict-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("prepare-games").assertIsEnabled()

        composeRule.runOnIdle { currentPreview = preparationPlan(platformConflict = true) }

        composeRule.onNodeWithTag("preparation-conflict-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("prepare-games").assertIsNotEnabled()
    }

    @Test
    fun preparationDetailsUseScrollableOverlayAndReturnToAccessibleReviewActions() {
        var detailsVisible by mutableStateOf(false)
        val detailedPlan = preparationPlan(gameCount = 12)
        composeRule.setContent {
            AdventurePadTheme {
                TestExternalLaunchersScreen(
                    page = ExternalLaunchersPage.REVIEW,
                    preview = detailedPlan,
                    showDetails = detailsVisible,
                    onToggleDetails = { detailsVisible = !detailsVisible },
                )
            }
        }

        composeRule.onNodeWithTag("prepare-games").assertIsDisplayed()
        composeRule.onNodeWithTag("view-preparation-details").performClick()
        composeRule.onNodeWithTag("preparation-details-overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation-details", useUnmergedTree = true)
            .performScrollToNode(hasText("Unchanged — Game 12"))
        composeRule.onNodeWithText("Unchanged — Game 12", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("close-preparation-details").performClick()
        composeRule.onNodeWithTag("prepare-games").assertIsDisplayed().assertIsEnabled()
    }

    private fun preparationPlan(
        gameConflict: Boolean = false,
        platformConflict: Boolean = false,
        gameCount: Int = 1,
    ) = ExternalLauncherPreparationPlan(
        games = CocoonExportPlan(
            (1..gameCount).map { index ->
                CocoonExportEntry(
                    targetId = if (gameCount == 1) "monkey-1" else "game-$index",
                    title = if (gameCount == 1) "Monkey Island" else "Game $index",
                    fileName = if (gameCount == 1) "Monkey Island.dpt" else "Game $index.dpt",
                    content = cocoonDptContent(if (gameCount == 1) "monkey-1" else "game-$index"),
                    action = if (gameConflict && index == 1) {
                        CocoonExportAction.CONFLICT
                    } else {
                        CocoonExportAction.UNCHANGED
                    },
                    reason = if (gameConflict && index == 1) {
                        "A user-created shortcut already uses this name."
                    } else {
                        null
                    },
                )
            },
        ),
        platformFile = PreparedFilePlan(
            launcherId = COCOON_LAUNCHER_ID,
            fileName = ADVENTUREPAD_PLATFORM_FILE_NAME,
            content = cocoonPlatformJson(),
            action = if (platformConflict) PreparedFileAction.CONFLICT else PreparedFileAction.UNCHANGED,
            reason = if (platformConflict) "The launcher setup file was changed outside AdventurePad." else null,
        ),
    )
}

@androidx.compose.runtime.Composable
private fun TestExternalLaunchersScreen(
    page: ExternalLaunchersPage,
    folderPermissionValid: Boolean = true,
    preview: ExternalLauncherPreparationPlan? = ExternalLauncherPreparationPlan(
        CocoonExportPlan(emptyList()),
        PreparedFilePlan(
            COCOON_LAUNCHER_ID,
            ADVENTUREPAD_PLATFORM_FILE_NAME,
            cocoonPlatformJson(),
            PreparedFileAction.UNCHANGED,
        ),
    ),
    onConfirmLauncher: () -> Unit = {},
    onFolderNext: () -> Unit = {},
    onRunSetupAgain: () -> Unit = {},
    onShowInstructions: () -> Unit = {},
    showDetails: Boolean = false,
    onToggleDetails: () -> Unit = {},
) {
    ExternalLaunchersScreen(
        launchers = SupportedExternalLaunchers,
        selectedLauncherId = COCOON_LAUNCHER_ID,
        detectedLauncherIds = setOf(COCOON_LAUNCHER_ID),
        page = page,
        libraryState = ScummVMLibraryState(
            connected = true,
            loading = false,
            targets = listOf(ScummVMTarget("monkey-1", "Monkey Island", "scumm", "monkey")),
        ),
        folderName = "Scummvm",
        folderPermissionValid = folderPermissionValid,
        preview = preview,
        busy = false,
        statusMessage = null,
        reviewingUpdate = false,
        preparedGameCount = 8,
        showDetails = showDetails,
        onBack = {},
        onSelectLauncher = {},
        onConfirmLauncher = onConfirmLauncher,
        onLauncherBack = {},
        onFolderBack = {},
        onFolderNext = onFolderNext,
        onFolderSkip = onFolderNext,
        onReviewBack = {},
        onChooseFolder = {},
        onRefreshPreview = {},
        onPrepare = {},
        onReviewUpdate = {},
        onRunSetupAgain = onRunSetupAgain,
        onShowInstructions = onShowInstructions,
        onFinishSuccess = {},
        onToggleDetails = onToggleDetails,
        onOpenLauncher = {},
    )
}
