package com.jamesmoran.adventurepad

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class PointerSpeed(
    val multiplier: Float,
    val label: String,
    internal val storedValue: String,
) {
    HALF(0.5f, "0.5×", "0.5"),
    THREE_QUARTERS(0.75f, "0.75×", "0.75"),
    NORMAL(1f, "1.0×", "1.0"),
    ONE_AND_QUARTER(1.25f, "1.25×", "1.25"),
    ONE_AND_HALF(1.5f, "1.5×", "1.5"),
    DOUBLE(2f, "2.0×", "2.0");

    companion object {
        val Default = NORMAL

        fun fromStoredValue(value: String?): PointerSpeed =
            entries.firstOrNull { it.storedValue == value } ?: Default
    }
}

internal data class RelativePointerDelta(
    val dx: Float,
    val dy: Float,
)

internal fun scaleRelativeDelta(
    rawDx: Float,
    rawDy: Float,
    pointerSpeed: PointerSpeed,
): RelativePointerDelta = RelativePointerDelta(
    dx = rawDx * pointerSpeed.multiplier,
    dy = rawDy * pointerSpeed.multiplier,
)

internal interface PointerSpeedStore {
    val pointerSpeed: Flow<PointerSpeed>

    suspend fun setPointerSpeed(pointerSpeed: PointerSpeed)
}

internal val Context.adventurePadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "adventure_pad_settings",
)

internal class DataStorePointerSpeedStore(
    private val dataStore: DataStore<Preferences>,
) : PointerSpeedStore {
    override val pointerSpeed: Flow<PointerSpeed> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            PointerSpeed.fromStoredValue(preferences[PointerSpeedKey])
        }

    override suspend fun setPointerSpeed(pointerSpeed: PointerSpeed) {
        dataStore.edit { preferences ->
            preferences[PointerSpeedKey] = pointerSpeed.storedValue
        }
    }

    private companion object {
        val PointerSpeedKey = stringPreferencesKey("pointer_speed")
    }
}

internal class PointerSpeedRepository(
    store: PointerSpeedStore,
    scope: CoroutineScope,
) {
    val pointerSpeed: StateFlow<PointerSpeed> = store.pointerSpeed.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PointerSpeed.Default,
    )

    private val settingsStore = store

    suspend fun setPointerSpeed(pointerSpeed: PointerSpeed) {
        settingsStore.setPointerSpeed(pointerSpeed)
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): PointerSpeedRepository =
            PointerSpeedRepository(
                store = DataStorePointerSpeedStore(context.applicationContext.adventurePadDataStore),
                scope = scope,
            )
    }
}
