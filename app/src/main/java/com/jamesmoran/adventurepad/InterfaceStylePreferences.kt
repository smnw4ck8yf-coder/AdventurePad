package com.jamesmoran.adventurepad

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class InterfaceStyle(val displayName: String) {
    STANDARD("Standard"),
    IMMERSIVE("Immersive"),
    ;

    companion object {
        fun fromId(id: String?): InterfaceStyle = entries.firstOrNull {
            it.name.equals(id, ignoreCase = true)
        } ?: STANDARD
    }
}

internal interface InterfaceStylePreferencesStore {
    val interfaceStyleId: Flow<String?>

    suspend fun setInterfaceStyleId(interfaceStyleId: String)
}

internal class DataStoreInterfaceStylePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : InterfaceStylePreferencesStore {
    override val interfaceStyleId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[InterfaceStyleIdKey] }

    override suspend fun setInterfaceStyleId(interfaceStyleId: String) {
        dataStore.edit { preferences -> preferences[InterfaceStyleIdKey] = interfaceStyleId }
    }

    private companion object {
        val InterfaceStyleIdKey = stringPreferencesKey("interface_style")
    }
}

internal class InterfaceStylePreferencesRepository(
    private val store: InterfaceStylePreferencesStore,
    scope: CoroutineScope,
) {
    val activeStyle: StateFlow<InterfaceStyle> = store.interfaceStyleId
        .map(InterfaceStyle::fromId)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = InterfaceStyle.STANDARD,
        )

    suspend fun selectStyle(style: InterfaceStyle) {
        store.setInterfaceStyleId(style.name.lowercase())
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope) = InterfaceStylePreferencesRepository(
            DataStoreInterfaceStylePreferencesStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}
