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
import org.junit.Test

class InterfaceStylePreferencesRepositoryTest {
    @Test fun freshStateIsStandard() = withRepository(FakeInterfaceStyleStore()) {
        assertEquals(InterfaceStyle.STANDARD, it.activeStyle.value)
    }

    @Test fun styleSwitchingIsPersistentAndDeterministic() = runBlocking {
        val store = FakeInterfaceStyleStore()
        withRepository(store) { it.selectStyle("monkey", InterfaceStyle.IMMERSIVE) }
        withRepository(store) {
            assertEquals(InterfaceStyle.IMMERSIVE, it.requestedStyle("monkey").first())
            it.selectStyle("monkey", InterfaceStyle.STANDARD)
            assertEquals(InterfaceStyle.STANDARD, it.requestedStyle("monkey").first())
        }
    }

    @Test fun unknownStyleFallsBackToStandard() = withRepository(FakeInterfaceStyleStore("future")) {
        assertEquals(InterfaceStyle.STANDARD, it.activeStyle.value)
    }

    @Test fun newGamesDefaultTheirRequestedStyleToImmersive() = runBlocking {
        withRepository(FakeInterfaceStyleStore()) {
            assertEquals(InterfaceStyle.IMMERSIVE, it.requestedStyle("monkey").first())
        }
    }

    @Test fun existingGlobalPreferenceIsRetainedAsMigrationFallback() = runBlocking {
        withRepository(FakeInterfaceStyleStore("standard")) {
            assertEquals(InterfaceStyle.STANDARD, it.requestedStyle("monkey").first())
        }
    }

    @Test fun gamePreferencesDoNotLeakBetweenGames() = runBlocking {
        withRepository(FakeInterfaceStyleStore()) {
            it.selectStyle("game-a", InterfaceStyle.STANDARD)

            assertEquals(InterfaceStyle.STANDARD, it.requestedStyle("game-a").first())
            assertEquals(InterfaceStyle.IMMERSIVE, it.requestedStyle("game-b").first())

            it.selectStyle("game-b", InterfaceStyle.IMMERSIVE)
            assertEquals(InterfaceStyle.STANDARD, it.requestedStyle("game-a").first())
            assertEquals(InterfaceStyle.IMMERSIVE, it.requestedStyle("game-b").first())
        }
    }

    private inline fun withRepository(
        store: InterfaceStylePreferencesStore,
        block: (InterfaceStylePreferencesRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            block(InterfaceStylePreferencesRepository(store, scope))
        } finally {
            scope.cancel()
        }
    }
}

private class FakeInterfaceStyleStore(initial: String? = null) : InterfaceStylePreferencesStore {
    private val value = MutableStateFlow(initial)
    private val gameValues = mutableMapOf<String, MutableStateFlow<String?>>()
    override val interfaceStyleId: Flow<String?> = value
    override fun interfaceStyleId(targetId: String): Flow<String?> =
        gameValues.getOrPut(targetId) { MutableStateFlow(null) }
    override suspend fun setInterfaceStyleId(targetId: String, interfaceStyleId: String) {
        gameValues.getOrPut(targetId) { MutableStateFlow(null) }.value = interfaceStyleId
    }
}
