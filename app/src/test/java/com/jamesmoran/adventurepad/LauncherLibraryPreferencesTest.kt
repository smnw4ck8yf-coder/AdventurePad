package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLibraryPreferencesTest {
    private val games = listOf(
        ScummVMTarget("zork", "Zork", "glk", "zork"),
        ScummVMTarget("alpha", "alpha Flight", "test", "alpha"),
        ScummVMTarget("monkey", "Monkey Island", "scumm", "monkey"),
    )

    @Test fun alphabeticalSortUsesDisplayedTitleCaseInsensitively() {
        val sorted = sortLauncherTargets(games, LauncherLibraryMetadata(), LauncherSortMode.ALPHABETICAL)

        assertEquals(listOf("alpha", "monkey", "zork"), sorted.map { it.targetId })
    }

    @Test fun recentlyPlayedUsesNewestLaunchFirstAndTitleForNeverPlayedGames() {
        val metadata = LauncherLibraryMetadata(
            lastPlayed = mapOf("alpha" to 100L, "zork" to 300L),
            loaded = true,
        )

        val sorted = sortLauncherTargets(games, metadata, LauncherSortMode.RECENTLY_PLAYED)

        assertEquals(listOf("zork", "alpha", "monkey"), sorted.map { it.targetId })
    }

    @Test fun manualOrderSurvivesSwitchingToAlphabeticalAndBack() {
        val metadata = LauncherLibraryMetadata(
            manualOrder = listOf("monkey", "zork", "alpha"),
            loaded = true,
        )

        assertEquals(
            listOf("alpha", "monkey", "zork"),
            sortLauncherTargets(games, metadata, LauncherSortMode.ALPHABETICAL).map { it.targetId },
        )
        assertEquals(
            listOf("monkey", "zork", "alpha"),
            sortLauncherTargets(games, metadata, LauncherSortMode.MANUAL).map { it.targetId },
        )
    }

    @Test fun reorderingMovesOnlyTheDraggedCardAndCanBeCancelledByDiscardingResult() {
        val original = listOf("alpha", "monkey", "zork")
        val reordered = reorderManualOrder(original, draggedId = "alpha", overId = "zork")

        assertEquals(listOf("monkey", "zork", "alpha"), reordered)
        assertEquals(listOf("alpha", "monkey", "zork"), original)
        assertEquals(original, reorderManualOrder(original, "missing", "zork"))
    }

    @Test fun progressiveReorderingMovesExactlyOneSlotRightOrLeft() {
        val original = listOf("a", "b", "c", "d", "e")

        assertEquals(
            listOf("a", "b", "d", "c", "e"),
            reorderManualOrderOneStep(original, draggedId = "c", targetIndex = 4),
        )
        assertEquals(
            listOf("a", "c", "b", "d", "e"),
            reorderManualOrderOneStep(original, draggedId = "c", targetIndex = 0),
        )
    }

    @Test fun repeatedProgressiveStepsTraverseSeveralSlotsAndRowBoundary() {
        var order = listOf("a", "b", "c", "d", "e", "f")
        order = reorderManualOrderOneStep(order, draggedId = "c", targetIndex = 5)
        assertEquals(listOf("a", "b", "d", "c", "e", "f"), order)
        order = reorderManualOrderOneStep(order, draggedId = "c", targetIndex = 5)

        // With three columns this second neighbouring move crosses from row one into row two.
        assertEquals(listOf("a", "b", "d", "e", "c", "f"), order)
    }

    @Test fun progressiveTargetAlwaysAdvancesOnlyOneAdjacentIndex() {
        assertEquals(3, progressiveReorderIndex(currentIndex = 2, targetIndex = 5, itemCount = 6))
        assertEquals(1, progressiveReorderIndex(currentIndex = 2, targetIndex = 0, itemCount = 6))
        assertEquals(2, progressiveReorderIndex(currentIndex = 2, targetIndex = 2, itemCount = 6))
    }

    @Test fun newGamesAppendAndRemovedGamesArePruned() {
        assertEquals(
            listOf("monkey", "alpha", "new-game"),
            reconcileManualOrder(
                manualOrder = listOf("removed", "monkey", "alpha"),
                targetIds = listOf("alpha", "monkey", "new-game"),
            ),
        )
    }

    @Test fun editOrderIsEnabledOnlyForManualMode() {
        assertTrue(canReorderLibrary(LauncherSortMode.MANUAL, editing = true))
        assertFalse(canReorderLibrary(LauncherSortMode.MANUAL, editing = false))
        assertFalse(canReorderLibrary(LauncherSortMode.ALPHABETICAL, editing = true))
        assertFalse(canReorderLibrary(LauncherSortMode.RECENTLY_PLAYED, editing = true))
    }

    @Test fun manualOrderAndLastPlayedCodecsRoundTripSpecialTargetIds() {
        val order = listOf("monkey:2", "target,with,commas", "unicode-élan")
        val lastPlayed = mapOf(order[0] to 100L, order[2] to 300L)

        assertEquals(order, decodeManualOrder(encodeManualOrder(order)))
        assertEquals(lastPlayed, decodeLastPlayed(encodeLastPlayed(lastPlayed)))
    }

    @Test fun metadataPersistsAcrossRepositoryRecreation() = runBlocking {
        val store = FakeLauncherLibraryMetadataStore()
        withRepository(store) { repository ->
            repository.setManualOrder(listOf("monkey", "alpha", "zork"))
            repository.recordLaunch("alpha", 1234L)
        }

        withRepository(store) { restored ->
            assertEquals(listOf("monkey", "alpha", "zork"), restored.metadata.value.manualOrder)
            assertEquals(1234L, restored.metadata.value.lastPlayed["alpha"])
        }
    }

    private inline fun withRepository(
        store: LauncherLibraryMetadataStore,
        block: (LauncherLibraryMetadataRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            block(LauncherLibraryMetadataRepository(store, scope))
        } finally {
            scope.cancel()
        }
    }
}

private class FakeLauncherLibraryMetadataStore : LauncherLibraryMetadataStore {
    private val persisted = MutableStateFlow(LauncherLibraryMetadata(loaded = true))
    override val metadata: Flow<LauncherLibraryMetadata> = persisted

    override suspend fun setManualOrder(order: List<String>) {
        persisted.value = persisted.value.copy(manualOrder = order)
    }

    override suspend fun recordLastPlayed(targetId: String, timestamp: Long) {
        persisted.value = persisted.value.copy(
            lastPlayed = persisted.value.lastPlayed + (targetId to timestamp),
        )
    }
}

class LauncherPointerRoutingTest {
    @Test fun activeLauncherReceivesLeftRightAndScrollEventsThroughSharedPipeline() {
        val events = mutableListOf<LauncherPointerEvent>()
        CursorDeltaCoordinator.setLauncherEventSink { event -> events += event; true }
        try {
            assertTrue(CursorDeltaCoordinator.publishButton(ScummVMButtonEvent.LEFT_BUTTON_DOWN))
            assertTrue(CursorDeltaCoordinator.publishButton(ScummVMButtonEvent.LEFT_BUTTON_UP))
            assertTrue(CursorDeltaCoordinator.publishButton(ScummVMButtonEvent.RIGHT_BUTTON_DOWN))
            assertTrue(CursorDeltaCoordinator.publishButton(ScummVMButtonEvent.RIGHT_BUTTON_UP))
            assertTrue(CursorDeltaCoordinator.publishVerticalScroll(24f))
            assertTrue(CursorDeltaCoordinator.publishGamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertTrue(CursorDeltaCoordinator.publishGamepadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertTrue(CursorDeltaCoordinator.publishJoystickAxis(TriggerAxisValue(1, 0x01, 20_000)))
            CursorDeltaCoordinator.releaseJoystickAxes()
        } finally {
            CursorDeltaCoordinator.setLauncherEventSink(null)
        }

        assertEquals(
            listOf(
                LauncherPointerEvent.Button(ScummVMButtonEvent.LEFT_BUTTON_DOWN),
                LauncherPointerEvent.Button(ScummVMButtonEvent.LEFT_BUTTON_UP),
                LauncherPointerEvent.Button(ScummVMButtonEvent.RIGHT_BUTTON_DOWN),
                LauncherPointerEvent.Button(ScummVMButtonEvent.RIGHT_BUTTON_UP),
                LauncherPointerEvent.VerticalScroll(24f),
                LauncherPointerEvent.Key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
                LauncherPointerEvent.Key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT),
                LauncherPointerEvent.JoystickAxis(TriggerAxisValue(1, 0x01, 20_000)),
                LauncherPointerEvent.ReleaseJoystick,
            ),
            events,
        )
    }
}
