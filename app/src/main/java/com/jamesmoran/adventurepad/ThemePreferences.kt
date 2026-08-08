package com.jamesmoran.adventurepad

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeDefinition
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal interface ThemePreferencesStore {
    val themeId: Flow<String?>

    suspend fun setThemeId(themeId: String)
}

internal class DataStoreThemePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : ThemePreferencesStore {
    override val themeId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[ThemeIdKey] }

    override suspend fun setThemeId(themeId: String) {
        dataStore.edit { preferences ->
            preferences[ThemeIdKey] = themeId
        }
    }

    private companion object {
        val ThemeIdKey = stringPreferencesKey("global_theme_id")
    }
}

internal class ThemePreferencesRepository(
    private val store: ThemePreferencesStore,
    scope: CoroutineScope,
) {
    val activeTheme: StateFlow<AdventurePadThemeDefinition> = store.themeId
        .map(AdventurePadThemes::fromId)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AdventurePadThemes.Default,
        )

    suspend fun selectTheme(theme: AdventurePadThemeDefinition) {
        store.setThemeId(AdventurePadThemes.fromId(theme.id).id)
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): ThemePreferencesRepository =
            ThemePreferencesRepository(
                store = DataStoreThemePreferencesStore(context.applicationContext.adventurePadDataStore),
                scope = scope,
            )
    }
}
