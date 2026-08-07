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

internal enum class DisplayMode {
    INTERFACE,
    TRACKPAD,
}

internal data class DisplayModePreferences(
    val preferredMode: DisplayMode = DisplayMode.INTERFACE,
)

internal fun displayModeFromStoredValue(value: String?): DisplayMode =
    value?.let { stored -> DisplayMode.entries.firstOrNull { it.name == stored } }
        ?: DisplayMode.INTERFACE

internal data class DisplayComposition(
    val mode: DisplayMode,
    val upperExpandedRequested: Boolean,
    val lowerPanelVisible: Boolean,
)

internal fun resolveDisplayComposition(
    preferences: DisplayModePreferences,
    crop: NormalizedCrop?,
    connected: Boolean,
    lowerSurfaceAvailable: Boolean,
): DisplayComposition {
    val canShowInterface = preferences.preferredMode == DisplayMode.INTERFACE &&
        crop?.isValid() == true && connected && lowerSurfaceAvailable
    return if (canShowInterface) {
        DisplayComposition(DisplayMode.INTERFACE, upperExpandedRequested = true, lowerPanelVisible = true)
    } else {
        DisplayComposition(DisplayMode.TRACKPAD, upperExpandedRequested = false, lowerPanelVisible = false)
    }
}

internal enum class PresentationOwner {
    TRACKPAD,
    SPLIT_VIEW,
    EDITOR,
}

/** One owner decides both EGL consumers; editor state always takes priority over runtime state. */
internal data class PresentationTarget(
    val owner: PresentationOwner,
    val mode: DisplayMode,
    val lowerCrop: NormalizedCrop,
    val upperCrop: NormalizedCrop,
    val runtimePanelRequested: Boolean,
)

internal fun resolvePresentationTarget(
    preferredMode: DisplayMode,
    savedSplit: InterfaceSplit?,
    editorSplit: InterfaceSplit?,
    connected: Boolean,
    activeSurfaceReady: Boolean,
): PresentationTarget {
    val validEditorSplit = editorSplit?.takeIf { it.isValid() }
    if (validEditorSplit != null) {
        val ready = connected && activeSurfaceReady
        return PresentationTarget(
            owner = PresentationOwner.EDITOR,
            mode = if (ready) DisplayMode.INTERFACE else DisplayMode.TRACKPAD,
            lowerCrop = NormalizedCrop.FullFrame,
            upperCrop = validEditorSplit.interfaceCrop,
            runtimePanelRequested = false,
        )
    }

    val validSavedSplit = savedSplit?.takeIf { it.isValid() }
    val splitRequested = preferredMode == DisplayMode.INTERFACE && validSavedSplit != null && connected
    val ready = splitRequested && activeSurfaceReady
    return if (splitRequested) {
        PresentationTarget(
            owner = PresentationOwner.SPLIT_VIEW,
            mode = if (ready) DisplayMode.INTERFACE else DisplayMode.TRACKPAD,
            lowerCrop = checkNotNull(validSavedSplit).interfaceCrop,
            upperCrop = validSavedSplit.interfaceCrop,
            runtimePanelRequested = true,
        )
    } else {
        PresentationTarget(
            owner = PresentationOwner.TRACKPAD,
            mode = DisplayMode.TRACKPAD,
            lowerCrop = NormalizedCrop.FullFrame,
            upperCrop = validSavedSplit?.interfaceCrop ?: NormalizedCrop.FullFrame,
            runtimePanelRequested = false,
        )
    }
}

internal enum class UpperExpansionEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
}

internal data class UpperGameplayRegion(
    val crop: NormalizedCrop,
    val interfaceEdge: UpperExpansionEdge,
)

internal fun deriveUpperGameplayRegion(
    interfaceCrop: NormalizedCrop,
    edgeTolerance: Float = 0.0001f,
): UpperGameplayRegion? {
    if (!interfaceCrop.isValid() || !edgeTolerance.isFinite() || edgeTolerance < 0f) return null
    val isLowerFullWidthRegion = interfaceCrop.left <= edgeTolerance &&
        interfaceCrop.right >= 1f - edgeTolerance && interfaceCrop.bottom >= 1f - edgeTolerance
    val result = when {
        isLowerFullWidthRegion -> UpperGameplayRegion(
            NormalizedCrop(0f, 0f, 1f, interfaceCrop.top),
            UpperExpansionEdge.BOTTOM,
        )
        else -> null
    }
    return result?.takeIf { it.crop.isValid() }
}

internal enum class UpperPresentationResult(val wireValue: Int) {
    FULL_FRAME_APPLIED(1),
    EXPANDED_APPLIED(2),
    EXPANDED_UNSUPPORTED_SHAPE(3),
    INVALID_CROP(4),
    STALE_GENERATION(5),
    UNSUPPORTED_RENDERER(6),
    ;

    companion object {
        fun fromWireValue(value: Int): UpperPresentationResult =
            entries.firstOrNull { it.wireValue == value } ?: UNSUPPORTED_RENDERER
    }
}

internal data class UpperPresentationAcknowledgement(
    val result: UpperPresentationResult,
    val modeGeneration: Long,
    val geometryGeneration: Long,
    val diagnostic: String,
)

internal data class UpperPresentationRequest(
    val mode: DisplayMode,
    val crop: NormalizedCrop,
    val geometryGeneration: Long,
)

internal class UpperPresentationGate {
    var pendingGeneration: Long? = null
        private set

    private var requested: UpperPresentationRequest? = null

    fun begin(generation: Long, request: UpperPresentationRequest): Boolean {
        if (generation <= 0 || !request.crop.isValid() || request.geometryGeneration <= 0) return false
        if (request == requested) return false
        pendingGeneration = generation
        requested = request
        return true
    }

    fun acknowledge(acknowledgement: UpperPresentationAcknowledgement): Boolean {
        if (acknowledgement.modeGeneration != pendingGeneration) return false
        pendingGeneration = null
        return true
    }

    fun cancel(invalidateRequest: Boolean = false) {
        pendingGeneration = null
        if (invalidateRequest) requested = null
    }
}

internal class TwoFingerDoubleTapResolver(private val timeoutMillis: Long) {
    private var pendingTapUptimeMillis: Long? = null

    fun onTwoFingerTap(uptimeMillis: Long): TwoFingerTapResolution {
        if (uptimeMillis < 0 || timeoutMillis <= 0) return TwoFingerTapResolution.NONE
        val pending = pendingTapUptimeMillis
        return if (pending != null && uptimeMillis - pending in 0..timeoutMillis) {
            pendingTapUptimeMillis = null
            TwoFingerTapResolution.TOGGLE_MODE
        } else {
            pendingTapUptimeMillis = uptimeMillis
            TwoFingerTapResolution.WAIT_FOR_SECOND_TAP
        }
    }

    fun resolveTimeout(expectedTapUptimeMillis: Long): Boolean {
        if (pendingTapUptimeMillis != expectedTapUptimeMillis) return false
        pendingTapUptimeMillis = null
        return true
    }

    fun cancelAndResolveSingleTap(): Boolean {
        val hadPendingTap = pendingTapUptimeMillis != null
        pendingTapUptimeMillis = null
        return hadPendingTap
    }

    fun reset() {
        pendingTapUptimeMillis = null
    }
}

internal enum class TwoFingerTapResolution {
    NONE,
    WAIT_FOR_SECOND_TAP,
    TOGGLE_MODE,
}

internal const val LEFT_TRIGGER_AXIS_FLAG = 0x40
internal const val RIGHT_TRIGGER_AXIS_FLAG = 0x80

internal data class TriggerAxisValue(val deviceId: Int, val axisFlag: Int, val position: Int)

internal data class TriggerChordResolution(
    val forward: List<TriggerAxisValue> = emptyList(),
    val toggle: Boolean = false,
    val scheduleTimeoutAt: Long? = null,
)

/** Delays the first trigger briefly so an L2+R2 chord cannot leak individual presses. */
internal class ControllerTriggerChordResolver(
    private val chordWindowMillis: Long,
    private val pressedThreshold: Int = 16_384,
) {
    private val queued = mutableListOf<TriggerAxisValue>()
    private var pendingSince: Long? = null
    private var passthrough = false
    private var chordLatched = false
    private var leftPosition = 0
    private var rightPosition = 0

    fun onAxis(value: TriggerAxisValue, uptimeMillis: Long): TriggerChordResolution {
        if (value.axisFlag != LEFT_TRIGGER_AXIS_FLAG && value.axisFlag != RIGHT_TRIGGER_AXIS_FLAG) {
            return TriggerChordResolution(forward = listOf(value))
        }
        if (value.axisFlag == LEFT_TRIGGER_AXIS_FLAG) leftPosition = value.position else rightPosition = value.position

        if (chordLatched) {
            if (!leftPressed() && !rightPressed()) reset()
            return TriggerChordResolution()
        }
        if (passthrough) {
            val result = TriggerChordResolution(forward = listOf(value))
            if (!leftPressed() && !rightPressed()) reset()
            return result
        }

        val started = pendingSince
        if (started != null && uptimeMillis - started > chordWindowMillis) {
            val flushed = flushPending().toMutableList()
            passthrough = true
            flushed += value
            if (!leftPressed() && !rightPressed()) reset()
            return TriggerChordResolution(forward = flushed)
        }

        queued += value
        if (pendingSince == null && (leftPressed() || rightPressed())) pendingSince = uptimeMillis
        if (leftPressed() && rightPressed()) {
            queued.clear()
            pendingSince = null
            chordLatched = true
            return TriggerChordResolution(toggle = true)
        }
        if (pendingSince == null) {
            val forward = flushPending()
            return TriggerChordResolution(forward = forward)
        }
        return TriggerChordResolution(scheduleTimeoutAt = checkNotNull(pendingSince) + chordWindowMillis)
    }

    fun onTimeout(expectedDeadline: Long): TriggerChordResolution {
        val started = pendingSince ?: return TriggerChordResolution()
        if (started + chordWindowMillis != expectedDeadline || chordLatched) return TriggerChordResolution()
        passthrough = true
        return TriggerChordResolution(forward = flushPending())
    }

    fun reset(): List<TriggerAxisValue> {
        val releases = queued.asSequence()
            .map { it.deviceId to it.axisFlag }
            .distinct()
            .map { (deviceId, axisFlag) -> TriggerAxisValue(deviceId, axisFlag, 0) }
            .toList()
        queued.clear()
        pendingSince = null
        passthrough = false
        chordLatched = false
        leftPosition = 0
        rightPosition = 0
        return releases
    }

    private fun flushPending(): List<TriggerAxisValue> = queued.toList().also {
        queued.clear()
        pendingSince = null
    }

    private fun leftPressed() = leftPosition >= pressedThreshold
    private fun rightPressed() = rightPosition >= pressedThreshold
}

internal data class LowerScreenLayout(
    val interfaceHeight: Int,
    val trackpadHeight: Int,
    val mouseButtonsTop: Int,
    val utilityBarTop: Int,
    val bottom: Int,
)

internal fun calculateLowerScreenLayout(
    availableWidth: Int,
    availableHeight: Int,
    interfaceAspectRatio: Float?,
    interfaceVisible: Boolean,
    mouseButtonsHeight: Int,
    utilityBarHeight: Int,
    minimumTrackpadHeight: Int,
): LowerScreenLayout? {
    if (availableWidth <= 0 || availableHeight <= 0 || mouseButtonsHeight < 0 ||
        utilityBarHeight < 0 || minimumTrackpadHeight < 0
    ) return null
    val reservedHeight = mouseButtonsHeight + utilityBarHeight + minimumTrackpadHeight
    val maximumInterfaceHeight = (availableHeight - reservedHeight).coerceAtLeast(0)
    val requestedInterfaceHeight = if (interfaceVisible) {
        interfaceAspectRatio?.takeIf { it.isFinite() && it > 0f }
            ?.let { (availableWidth / it).toInt().coerceAtLeast(0) }
            ?: 0
    } else 0
    val interfaceHeight = requestedInterfaceHeight.coerceAtMost(maximumInterfaceHeight)
    val trackpadHeight = availableHeight - interfaceHeight - mouseButtonsHeight - utilityBarHeight
    val mouseButtonsTop = interfaceHeight + trackpadHeight
    val utilityBarTop = mouseButtonsTop + mouseButtonsHeight
    return LowerScreenLayout(
        interfaceHeight = interfaceHeight,
        trackpadHeight = trackpadHeight,
        mouseButtonsTop = mouseButtonsTop,
        utilityBarTop = utilityBarTop,
        bottom = utilityBarTop + utilityBarHeight,
    )
}

internal interface DisplayModePreferencesStore {
    val preferences: Flow<DisplayModePreferences>
    suspend fun save(preferences: DisplayModePreferences)
}

internal class DataStoreDisplayModePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : DisplayModePreferencesStore {
    override val preferences: Flow<DisplayModePreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { stored ->
            DisplayModePreferences(
                preferredMode = displayModeFromStoredValue(stored[PreferredMode]),
            )
        }

    override suspend fun save(preferences: DisplayModePreferences) {
        dataStore.edit { stored ->
            stored[PreferredMode] = preferences.preferredMode.name
        }
    }

    private companion object {
        val PreferredMode = stringPreferencesKey("preferred_display_mode")
    }
}

internal class DisplayModePreferencesRepository(store: DisplayModePreferencesStore, scope: CoroutineScope) {
    private val preferencesStore = store
    val preferences: StateFlow<DisplayModePreferences> = store.preferences.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DisplayModePreferences(),
    )

    suspend fun save(preferences: DisplayModePreferences) = preferencesStore.save(preferences)

    companion object {
        fun create(context: Context, scope: CoroutineScope) = DisplayModePreferencesRepository(
            DataStoreDisplayModePreferencesStore(context.applicationContext.adventurePadDataStore),
            scope,
        )
    }
}
