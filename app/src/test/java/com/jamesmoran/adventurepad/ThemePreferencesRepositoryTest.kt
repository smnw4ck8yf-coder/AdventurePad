package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemePreferencesRepositoryTest {
    @Test
    fun freshStateSelectsDefault() {
        withRepository(FakePersistentThemePreferencesStore()) { repository ->
            assertSame(AdventurePadThemes.Default, repository.activeTheme.value)
        }
    }

    @Test
    fun oceanCanBeSelectedAndUpdatesActiveThemeDefinition() = runBlocking {
        val store = FakePersistentThemePreferencesStore()
        withRepository(store) { repository ->
            repository.selectTheme(AdventurePadThemes.Ocean)

            assertSame(AdventurePadThemes.Ocean, repository.activeTheme.value)
        }
    }

    @Test
    fun selectedThemePersistsAcrossRepositoryRecreation() = runBlocking {
        val store = FakePersistentThemePreferencesStore()
        withRepository(store) { repository ->
            repository.selectTheme(AdventurePadThemes.Ocean)
        }

        withRepository(store) { restoredRepository ->
            assertSame(AdventurePadThemes.Ocean, restoredRepository.activeTheme.value)
            assertEquals("ocean", store.persistedThemeId)
        }
    }

    @Test
    fun adventurePersistsAcrossRepositoryRecreation() = runBlocking {
        val store = FakePersistentThemePreferencesStore()
        withRepository(store) { repository ->
            repository.selectTheme(AdventurePadThemes.Adventure)
        }

        withRepository(store) { restoredRepository ->
            assertSame(AdventurePadThemes.Adventure, restoredRepository.activeTheme.value)
            assertEquals("adventure", store.persistedThemeId)
        }
    }

    @Test
    fun unknownThemeIdFallsBackToDefault() {
        withRepository(FakePersistentThemePreferencesStore("removed-theme")) { repository ->
            assertSame(AdventurePadThemes.Default, repository.activeTheme.value)
        }
    }

    @Test
    fun readerPalettesFollowSelectedTheme() = runBlocking {
        val store = FakePersistentThemePreferencesStore()
        withRepository(store) { repository ->
            val defaultPalette = repository.activeTheme.value.readerPalette(ReadingAppearance.DARK)

            repository.selectTheme(AdventurePadThemes.Ocean)

            assertEquals(
                AdventurePadThemes.Ocean.readerPalette(ReadingAppearance.DARK),
                repository.activeTheme.value.readerPalette(ReadingAppearance.DARK),
            )
            assertNotEquals(defaultPalette, repository.activeTheme.value.readerPalette(ReadingAppearance.DARK))
        }
    }

    @Test
    fun themeChangesDoNotChangeSettingsOrCompanionNavigationState() = runBlocking {
        val store = FakePersistentThemePreferencesStore()
        val settingsState = LowerScreenNavigationState(
            page = LowerScreenPage.SETTINGS,
            companionSection = CompanionSection.WALKTHROUGH,
        )

        withRepository(store) { repository ->
            repository.selectTheme(AdventurePadThemes.Ocean)

            assertEquals(LowerScreenPage.SETTINGS, settingsState.page)
            assertEquals(CompanionSection.WALKTHROUGH, settingsState.companionSection)
        }
    }

    private inline fun withRepository(
        store: ThemePreferencesStore,
        block: (ThemePreferencesRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            block(ThemePreferencesRepository(store, scope))
        } finally {
            scope.cancel()
        }
    }
}

private class FakePersistentThemePreferencesStore(
    initialThemeId: String? = null,
) : ThemePreferencesStore {
    private val persistedValue = MutableStateFlow(initialThemeId)

    val persistedThemeId: String?
        get() = persistedValue.value

    override val themeId: Flow<String?> = persistedValue

    override suspend fun setThemeId(themeId: String) {
        persistedValue.value = themeId
    }
}
