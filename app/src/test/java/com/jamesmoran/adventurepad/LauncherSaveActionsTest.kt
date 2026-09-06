package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSaveActionsTest {
    @Test
    fun gameWithNoSaveCannotResume() {
        val target = target("monkey")

        assertFalse(target.resumeGameAvailable)
        assertNull(target.resumeSaveSlot)
    }

    @Test
    fun gameWithIdentifiedSaveCanResumeThatExactSlot() {
        val target = target("monkey", resumeSaveSlot = 17)

        assertTrue(target.resumeGameAvailable)
        assertEquals(17, target.resumeSaveSlot)
    }

    @Test
    fun invalidSaveSlotFailsClosed() {
        val target = target("broken", resumeSaveSlot = -1)

        assertFalse(target.resumeGameAvailable)
    }

    @Test
    fun loadAvailabilityComesFromSelectedTargetsScummVMCapability() {
        val supported = target("samnmax", loadGameAvailable = true)
        val unsupported = target("monkey", loadGameAvailable = false)

        assertTrue(supported.loadGameAvailable)
        assertFalse(unsupported.loadGameAvailable)
    }

    @Test
    fun saveActionsRemainBoundToTheSelectedTarget() {
        val previous = target("monkey", resumeSaveSlot = 2)
        val selected = target("samnmax", resumeSaveSlot = 9, loadGameAvailable = true)

        assertEquals("samnmax", selected.targetId)
        assertEquals(9, selected.resumeSaveSlot)
        assertTrue(selected.loadGameAvailable)
        assertEquals(2, previous.resumeSaveSlot)
    }

    @Test
    fun normalCardActivationStillPlaysUnlessOrderIsBeingEdited() {
        assertTrue(shouldLaunchGameFromCard(editingOrder = false))
        assertFalse(shouldLaunchGameFromCard(editingOrder = true))
    }

    @Test
    fun coldConnectionRequestsSaveCapabilityRefreshExactlyOnce() {
        val gate = SaveCapabilityRefreshRequestGate()
        var sends = 0

        assertTrue(gate.requestOnce { sends += 1; true })
        assertFalse(gate.requestOnce { sends += 1; true })
        assertEquals(1, sends)
    }

    @Test
    fun failedRefreshSendCanBeRetried() {
        val gate = SaveCapabilityRefreshRequestGate()
        var sends = 0

        assertFalse(gate.requestOnce { sends += 1; false })
        assertTrue(gate.requestOnce { sends += 1; true })
        assertEquals(2, sends)
    }

    @Test
    fun reconnectAllowsOneFreshCapabilityRequest() {
        val gate = SaveCapabilityRefreshRequestGate()
        var sends = 0

        assertTrue(gate.requestOnce { sends += 1; true })
        gate.reset()
        assertTrue(gate.requestOnce { sends += 1; true })
        assertEquals(2, sends)
    }

    @Test
    fun refreshedCapabilityPublicationReplacesNoSaveState() {
        val cold = ScummVMLibraryState(
            targets = listOf(target("monkey")),
            saveCapabilitiesReady = false,
        )
        val refreshed = cold.copy(
            targets = listOf(target("monkey", resumeSaveSlot = 17, loadGameAvailable = true)),
            saveCapabilitiesReady = true,
        )

        assertFalse(cold.targets.single().resumeGameAvailable)
        assertTrue(refreshed.targets.single().resumeGameAvailable)
        assertEquals(17, refreshed.targets.single().resumeSaveSlot)
    }

    private fun target(
        id: String,
        resumeSaveSlot: Int? = null,
        loadGameAvailable: Boolean = false,
    ) = ScummVMTarget(
        targetId = id,
        title = id,
        engineId = "scumm",
        gameId = id,
        resumeSaveSlot = resumeSaveSlot,
        loadGameAvailable = loadGameAvailable,
    )
}
