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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class LauncherSortMode(val label: String) {
    MANUAL("Manual"),
    ALPHABETICAL("Alphabetical"),
    RECENTLY_PLAYED("Recently Played"),
}

internal data class LauncherLibraryMetadata(
    val manualOrder: List<String> = emptyList(),
    val lastPlayed: Map<String, Long> = emptyMap(),
    val loaded: Boolean = false,
)

internal fun sortLauncherTargets(
    targets: List<ScummVMTarget>,
    metadata: LauncherLibraryMetadata,
    mode: LauncherSortMode,
): List<ScummVMTarget> = when (mode) {
    LauncherSortMode.MANUAL -> {
        val positions = metadata.manualOrder.withIndex().associate { it.value to it.index }
        targets.sortedWith(
            compareBy<ScummVMTarget> { positions[it.targetId] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }
    LauncherSortMode.ALPHABETICAL -> targets.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LauncherSortMode.RECENTLY_PLAYED -> targets.sortedWith(
        compareByDescending<ScummVMTarget> { metadata.lastPlayed[it.targetId] ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

internal fun reconcileManualOrder(
    manualOrder: List<String>,
    targetIds: List<String>,
): List<String> {
    val current = targetIds.filter(String::isNotBlank).distinct()
    val currentSet = current.toHashSet()
    return buildList {
        manualOrder.forEach { if (it in currentSet && it !in this) add(it) }
        current.forEach { if (it !in this) add(it) }
    }
}

internal fun reorderManualOrder(
    manualOrder: List<String>,
    draggedId: String,
    overId: String,
): List<String> {
    val from = manualOrder.indexOf(draggedId)
    val to = manualOrder.indexOf(overId)
    if (from < 0 || to < 0 || from == to) return manualOrder
    return manualOrder.toMutableList().apply { add(to, removeAt(from)) }
}

/** Moves a dragged item by at most one neighbouring slot toward [targetIndex]. */
internal fun reorderManualOrderOneStep(
    manualOrder: List<String>,
    draggedId: String,
    targetIndex: Int,
): List<String> {
    val from = manualOrder.indexOf(draggedId)
    if (from < 0 || targetIndex !in manualOrder.indices || from == targetIndex) return manualOrder
    val adjacentIndex = if (targetIndex > from) from + 1 else from - 1
    return manualOrder.toMutableList().apply {
        add(adjacentIndex, removeAt(from))
    }
}

internal fun progressiveReorderIndex(
    currentIndex: Int,
    targetIndex: Int,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount || targetIndex !in 0 until itemCount) {
        return currentIndex
    }
    return when {
        targetIndex > currentIndex -> currentIndex + 1
        targetIndex < currentIndex -> currentIndex - 1
        else -> currentIndex
    }
}

internal fun canReorderLibrary(mode: LauncherSortMode, editing: Boolean): Boolean =
    mode == LauncherSortMode.MANUAL && editing

internal interface LauncherLibraryMetadataStore {
    val metadata: Flow<LauncherLibraryMetadata>
    suspend fun setManualOrder(order: List<String>)
    suspend fun recordLastPlayed(targetId: String, timestamp: Long)
}

internal class DataStoreLauncherLibraryMetadataStore(
    private val dataStore: DataStore<Preferences>,
) : LauncherLibraryMetadataStore {
    override val metadata: Flow<LauncherLibraryMetadata> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            LauncherLibraryMetadata(
                manualOrder = decodeManualOrder(preferences[ManualOrderKey]),
                lastPlayed = decodeLastPlayed(preferences[LastPlayedKey]),
                loaded = true,
            )
        }

    override suspend fun setManualOrder(order: List<String>) {
        dataStore.edit { it[ManualOrderKey] = encodeManualOrder(order) }
    }

    override suspend fun recordLastPlayed(targetId: String, timestamp: Long) {
        if (targetId.isBlank() || timestamp <= 0L) return
        dataStore.edit { preferences ->
            val updated = decodeLastPlayed(preferences[LastPlayedKey]).toMutableMap()
            updated[targetId] = timestamp
            preferences[LastPlayedKey] = encodeLastPlayed(updated)
        }
    }

    private companion object {
        val ManualOrderKey = stringPreferencesKey("launcher_manual_order")
        val LastPlayedKey = stringPreferencesKey("launcher_last_played")
    }
}

internal class LauncherLibraryMetadataRepository(
    private val store: LauncherLibraryMetadataStore,
    scope: CoroutineScope,
) {
    val metadata: StateFlow<LauncherLibraryMetadata> = store.metadata.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherLibraryMetadata(),
    )

    suspend fun reconcileTargets(targetIds: List<String>) {
        val current = metadata.value
        if (!current.loaded) return
        val reconciled = reconcileManualOrder(current.manualOrder, targetIds)
        if (reconciled != current.manualOrder) store.setManualOrder(reconciled)
    }

    suspend fun reorder(draggedId: String, overId: String) {
        val current = metadata.value
        if (!current.loaded) return
        val reordered = reorderManualOrder(current.manualOrder, draggedId, overId)
        if (reordered != current.manualOrder) store.setManualOrder(reordered)
    }

    suspend fun setManualOrder(order: List<String>) {
        if (!metadata.value.loaded) return
        store.setManualOrder(order)
    }

    suspend fun recordLaunch(targetId: String, timestamp: Long = System.currentTimeMillis()) {
        store.recordLastPlayed(targetId, timestamp)
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope) = LauncherLibraryMetadataRepository(
            DataStoreLauncherLibraryMetadataStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}

internal fun encodeManualOrder(order: List<String>): String = order
    .filter(String::isNotBlank)
    .distinct()
    .joinToString(",", transform = ::encodeId)

internal fun decodeManualOrder(encoded: String?): List<String> = encoded.orEmpty()
    .split(',')
    .mapNotNull(::decodeId)
    .filter(String::isNotBlank)
    .distinct()

internal fun encodeLastPlayed(lastPlayed: Map<String, Long>): String = lastPlayed.entries
    .asSequence()
    .filter { it.key.isNotBlank() && it.value > 0L }
    .sortedBy { it.key }
    .joinToString(",") { "${encodeId(it.key)}:${it.value}" }

internal fun decodeLastPlayed(encoded: String?): Map<String, Long> = encoded.orEmpty()
    .split(',')
    .mapNotNull { entry ->
        val separator = entry.lastIndexOf(':')
        if (separator <= 0) return@mapNotNull null
        val id = decodeId(entry.substring(0, separator)) ?: return@mapNotNull null
        val timestamp = entry.substring(separator + 1).toLongOrNull()?.takeIf { it > 0L }
            ?: return@mapNotNull null
        id to timestamp
    }
    .toMap()

private fun encodeId(id: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(id.toByteArray(StandardCharsets.UTF_8))

private fun decodeId(encoded: String): String? = try {
    String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
} catch (_: IllegalArgumentException) {
    null
}
