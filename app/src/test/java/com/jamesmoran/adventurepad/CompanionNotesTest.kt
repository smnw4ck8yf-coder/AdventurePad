package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionNotesTest {
    @Test fun freshRepositoryDoesNotReadOrWriteLauncherNotesWithoutATarget() = runBlocking {
        val store = FakeCompanionNotesStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = CompanionNotesRepository(store, scope)

        assertEquals(CompanionNotesSelection("", ""), repository.selection.value)
        assertTrue(store.requestedGameIds.isEmpty())
        scope.cancel()
    }

    @Test fun notesPersistForTheCurrentTargetAndRemainIsolatedPerGame() = runBlocking {
        val store = FakeCompanionNotesStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = CompanionNotesRepository(store, scope)

        repository.selectGame("monkey2")
        repository.save("monkey2", "Ask about the voodoo doll")
        assertEquals("Ask about the voodoo doll", repository.selection.value.notes)

        repository.selectGame("atlantis")
        assertEquals("", repository.selection.value.notes)
        repository.save("atlantis", "Look for the lost dialogue")

        repository.selectGame("monkey2")
        assertEquals("Ask about the voodoo doll", repository.selection.value.notes)
        scope.cancel()
    }

    @Test fun targetKeysAreStableAndDoNotCollideWithLauncher() {
        assertEquals(gameStorageKey("monkey2"), gameStorageKey("monkey2"))
        assertNotEquals(gameStorageKey("monkey2"), gameStorageKey("atlantis"))
        assertEquals(gameStorageKey(""), gameStorageKey("launcher"))
    }

    @Test fun walkthroughPassagesAppendToOnlyTheRequestedGame() = runBlocking {
        val store = FakeCompanionNotesStore()
        store.save("game-a", "Existing")
        store.save("game-b", "Other")
        store.appendWalkthrough("game-a", "Use the lamp.", "Temple")

        val gameA = store.notes("game-a").first()
        val gameB = store.notes("game-b").first()
        assertTrue(gameA.startsWith("Existing"))
        assertTrue(gameA.contains("Walkthrough: Temple"))
        assertEquals("Other", gameB)
    }

    @Test fun notesEditorUsesDoneAndDoneSavesBeforeFinishing() {
        var saved = ""
        var finished = false

        completeNotesEdit("Use the rubber chicken", { saved = it }, { finished = true })

        assertEquals("DONE", NOTES_EDITOR_ACTION_LABEL)
        assertEquals("Use the rubber chicken", saved)
        assertTrue(finished)
    }
}

private class FakeCompanionNotesStore : CompanionNotesStore {
    private val notesByGame = mutableMapOf<String, MutableStateFlow<String>>()
    val requestedGameIds = mutableListOf<String>()

    override fun notes(gameId: String): Flow<String> {
        requestedGameIds += gameId
        return notesByGame.getOrPut(gameId) { MutableStateFlow("") }
    }

    override suspend fun save(gameId: String, notes: String) {
        notesByGame.getOrPut(gameId) { MutableStateFlow("") }.value = notes
    }

    override suspend fun appendWalkthrough(gameId: String, passage: String, sectionTitle: String?) {
        notesByGame.getOrPut(gameId) { MutableStateFlow("") }.value =
            appendWalkthroughToNotes(notesByGame[gameId]?.value.orEmpty(), passage, sectionTitle)
    }
}
