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
import kotlinx.coroutines.flow.combine
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
    /** Legacy global preference retained as a migration fallback. */
    val interfaceStyleId: Flow<String?>

    fun interfaceStyleId(targetId: String): Flow<String?>

    suspend fun setInterfaceStyleId(targetId: String, interfaceStyleId: String)
}

internal class DataStoreInterfaceStylePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : InterfaceStylePreferencesStore {
    override val interfaceStyleId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[InterfaceStyleIdKey] }

    override fun interfaceStyleId(targetId: String): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[gameInterfaceStyleKey(targetId)] }

    override suspend fun setInterfaceStyleId(targetId: String, interfaceStyleId: String) {
        dataStore.edit { preferences ->
            preferences[gameInterfaceStyleKey(targetId)] = interfaceStyleId
        }
    }

    private companion object {
        val InterfaceStyleIdKey = stringPreferencesKey("interface_style")
        private const val GameInterfaceStylePrefix = "interface_style.game."

        fun gameInterfaceStyleKey(targetId: String) =
            stringPreferencesKey(GameInterfaceStylePrefix + targetId.trim())
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

    /**
     * Returns the requested presentation for one game. Existing global preferences are used only
     * when that game has no explicit choice; a genuinely new game defaults to Immersive once a
     * capable skin is applied. The effective style is resolved separately from SkinContext.
     */
    fun requestedStyle(targetId: String): Flow<InterfaceStyle> {
        val normalizedTargetId = targetId.trim()
        if (normalizedTargetId.isEmpty()) return activeStyle
        return combine(
            store.interfaceStyleId(normalizedTargetId),
            store.interfaceStyleId,
        ) { gameStyleId, legacyGlobalStyleId ->
            gameStyleId?.let(InterfaceStyle::fromId)
                ?: legacyGlobalStyleId?.let(InterfaceStyle::fromId)
                ?: InterfaceStyle.IMMERSIVE
        }
    }

    suspend fun selectStyle(targetId: String, style: InterfaceStyle) {
        require(targetId.isNotBlank())
        store.setInterfaceStyleId(targetId.trim(), style.name.lowercase())
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope) = InterfaceStylePreferencesRepository(
            DataStoreInterfaceStylePreferencesStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}
