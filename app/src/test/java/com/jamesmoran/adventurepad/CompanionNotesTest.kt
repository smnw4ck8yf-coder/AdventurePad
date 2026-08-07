package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CompanionNotesTest {
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
}

private class FakeCompanionNotesStore : CompanionNotesStore {
    private val notesByGame = mutableMapOf<String, MutableStateFlow<String>>()

    override fun notes(gameId: String): Flow<String> = notesByGame.getOrPut(gameId) { MutableStateFlow("") }

    override suspend fun save(gameId: String, notes: String) {
        notesByGame.getOrPut(gameId) { MutableStateFlow("") }.value = notes
    }
}
