package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceStylePreferencesRepositoryTest {
    @Test fun freshStateIsStandard() = withRepository(FakeInterfaceStyleStore()) {
        assertEquals(InterfaceStyle.STANDARD, it.activeStyle.value)
    }

    @Test fun styleSwitchingIsPersistentAndDeterministic() = runBlocking {
        val store = FakeInterfaceStyleStore()
        withRepository(store) { it.selectStyle(InterfaceStyle.IMMERSIVE) }
        withRepository(store) {
            assertEquals(InterfaceStyle.IMMERSIVE, it.activeStyle.value)
            it.selectStyle(InterfaceStyle.STANDARD)
            assertEquals(InterfaceStyle.STANDARD, it.activeStyle.value)
        }
    }

    @Test fun unknownStyleFallsBackToStandard() = withRepository(FakeInterfaceStyleStore("future")) {
        assertEquals(InterfaceStyle.STANDARD, it.activeStyle.value)
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
    override val interfaceStyleId: Flow<String?> = value
    override suspend fun setInterfaceStyleId(interfaceStyleId: String) { value.value = interfaceStyleId }
}
