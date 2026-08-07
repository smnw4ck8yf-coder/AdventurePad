package com.jamesmoran.adventurepad

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal interface CompanionNotesStore {
    fun notes(gameId: String): Flow<String>
    suspend fun save(gameId: String, notes: String)
}

internal class DataStoreCompanionNotesStore(
    private val dataStore: DataStore<Preferences>,
) : CompanionNotesStore {
    override fun notes(gameId: String): Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[notesKey(gameId)]?.take(MAX_NOTES_LENGTH).orEmpty() }

    override suspend fun save(gameId: String, notes: String) {
        dataStore.edit { preferences ->
            preferences[notesKey(gameId)] = notes.take(MAX_NOTES_LENGTH)
        }
    }

    private fun notesKey(gameId: String) = stringPreferencesKey("companion_notes_${gameStorageKey(gameId)}")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class CompanionNotesRepository(
    private val store: CompanionNotesStore,
    scope: CoroutineScope,
) {
    private val activeGameId = MutableStateFlow("")
    val selection: StateFlow<CompanionNotesSelection> = activeGameId
        .flatMapLatest { gameId ->
            store.notes(gameId).map { notes -> CompanionNotesSelection(gameId, notes) }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = CompanionNotesSelection("", ""),
        )

    fun selectGame(gameId: String) {
        activeGameId.value = gameId
    }

    suspend fun save(gameId: String, notes: String) = store.save(gameId, notes)

    companion object {
        fun create(context: Context, scope: CoroutineScope) = CompanionNotesRepository(
            DataStoreCompanionNotesStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}

internal data class CompanionNotesSelection(
    val gameId: String,
    val notes: String,
)

internal fun gameStorageKey(gameId: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(gameId.ifBlank { "launcher" }.toByteArray(StandardCharsets.UTF_8))

internal const val MAX_NOTES_LENGTH = 100_000
