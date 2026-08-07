package com.jamesmoran.adventurepad

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import java.nio.charset.StandardCharsets
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

internal interface MirrorCropStore {
    fun profile(gameId: String): Flow<MirrorCropProfile>
    suspend fun save(gameId: String, profile: MirrorCropProfile)
}

internal class DataStoreMirrorCropStore(
    private val dataStore: DataStore<Preferences>,
) : MirrorCropStore {
    override fun profile(gameId: String): Flow<MirrorCropProfile> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { decode(it, gameId) }

    override suspend fun save(gameId: String, profile: MirrorCropProfile) {
        val suffix = gameKey(gameId)
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("mirror_split_schema_$suffix")] = profile.schemaVersion
            preferences[floatPreferencesKey("mirror_split_ratio_$suffix")] = profile.split.ratio
            preferences[intPreferencesKey("mirror_split_source_width_$suffix")] = profile.sourceWidth
            preferences[intPreferencesKey("mirror_split_source_height_$suffix")] = profile.sourceHeight
            preferences[floatPreferencesKey("mirror_split_source_aspect_$suffix")] = profile.sourceAspectRatio
            preferences[booleanPreferencesKey("mirror_split_confirmed_$suffix")] = profile.confirmed
            preferences[booleanPreferencesKey("mirror_split_requires_review_$suffix")] = profile.requiresReview
        }
    }

    private fun decode(preferences: Preferences, gameId: String): MirrorCropProfile {
        val suffix = gameKey(gameId)
        val perGameSchema = intPreferencesKey("mirror_split_schema_$suffix")
        val storedSchema = preferences[perGameSchema] ?: preferences[Schema] ?: 0
        val split = when (storedSchema) {
            MIRROR_CROP_SCHEMA_VERSION -> InterfaceSplit(
                preferences[floatPreferencesKey("mirror_split_ratio_$suffix")]
                    ?: preferences[SplitRatio]
                    ?: return MirrorCropProfile.Empty,
            )
            LEGACY_MIRROR_CROP_SCHEMA_VERSION -> {
                val legacyCrop = NormalizedCrop(
                    left = preferences[Left] ?: return MirrorCropProfile.Empty,
                    top = preferences[Top] ?: return MirrorCropProfile.Empty,
                    right = preferences[Right] ?: return MirrorCropProfile.Empty,
                    bottom = preferences[Bottom] ?: return MirrorCropProfile.Empty,
                )
                if (!legacyCrop.isValid()) return MirrorCropProfile.Empty
                InterfaceSplit.fromLegacyCrop(legacyCrop)
            }
            else -> return MirrorCropProfile.Empty
        }
        val profile = MirrorCropProfile(
            split = split,
            sourceWidth = preferences[intPreferencesKey("mirror_split_source_width_$suffix")]
                ?: preferences[SourceWidth] ?: 0,
            sourceHeight = preferences[intPreferencesKey("mirror_split_source_height_$suffix")]
                ?: preferences[SourceHeight] ?: 0,
            sourceAspectRatio = preferences[floatPreferencesKey("mirror_split_source_aspect_$suffix")]
                ?: preferences[SourceAspect] ?: 0f,
            // A valid v1 rectangle is migrated in memory from its interface top boundary.
            schemaVersion = MIRROR_CROP_SCHEMA_VERSION,
            confirmed = preferences[booleanPreferencesKey("mirror_split_confirmed_$suffix")]
                ?: preferences[Confirmed] ?: false,
            requiresReview = preferences[booleanPreferencesKey("mirror_split_requires_review_$suffix")]
                ?: preferences[RequiresReview] ?: true,
        )
        return profile.takeIf {
            it.split.isValid() && it.sourceWidth > 0 && it.sourceHeight > 0 &&
                it.sourceAspectRatio.isFinite() && it.sourceAspectRatio > 0f
        } ?: MirrorCropProfile.Empty
    }

    private companion object {
        fun gameKey(gameId: String): String = Base64.encodeToString(
            gameId.ifBlank { "launcher" }.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
        val Schema = intPreferencesKey("mirror_crop_schema")
        val SplitRatio = floatPreferencesKey("mirror_split_ratio")
        val Left = floatPreferencesKey("mirror_crop_left")
        val Top = floatPreferencesKey("mirror_crop_top")
        val Right = floatPreferencesKey("mirror_crop_right")
        val Bottom = floatPreferencesKey("mirror_crop_bottom")
        val SourceWidth = intPreferencesKey("mirror_crop_source_width")
        val SourceHeight = intPreferencesKey("mirror_crop_source_height")
        val SourceAspect = floatPreferencesKey("mirror_crop_source_aspect")
        val Confirmed = booleanPreferencesKey("mirror_crop_confirmed")
        val RequiresReview = booleanPreferencesKey("mirror_crop_requires_review")
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class MirrorCropRepository(store: MirrorCropStore, scope: CoroutineScope) {
    private val cropStore = store
    private val activeGameId = MutableStateFlow("")
    val selection: StateFlow<MirrorCropSelection> = activeGameId.flatMapLatest { gameId ->
        store.profile(gameId).map { profile -> MirrorCropSelection(gameId, profile) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = MirrorCropSelection("", MirrorCropProfile.Empty),
    )

    fun selectGame(gameId: String) {
        activeGameId.value = gameId
    }

    suspend fun save(profile: MirrorCropProfile) = cropStore.save(activeGameId.value, profile)

    companion object {
        fun create(context: Context, scope: CoroutineScope) = MirrorCropRepository(
            DataStoreMirrorCropStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}

internal data class MirrorCropSelection(
    val gameId: String,
    val profile: MirrorCropProfile,
)
