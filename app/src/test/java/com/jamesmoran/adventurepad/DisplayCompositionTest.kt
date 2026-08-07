package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayCompositionTest {
    private val bottomCrop = NormalizedCrop(0f, 0.75f, 1f, 1f)

    @Test fun preferredSplitViewAndValidCropCanEnterInterface() {
        val result = resolveDisplayComposition(DisplayModePreferences(), bottomCrop, true, true)
        assertEquals(DisplayMode.INTERFACE, result.mode)
        assertTrue(result.lowerPanelVisible)
        assertTrue(result.upperExpandedRequested)
    }

    @Test fun preferredTrackpadNeverRequestsExpansion() {
        val result = resolveDisplayComposition(
            DisplayModePreferences(preferredMode = DisplayMode.TRACKPAD), bottomCrop, true, true,
        )
        assertEquals(DisplayMode.TRACKPAD, result.mode)
        assertFalse(result.upperExpandedRequested)
    }

    @Test fun invalidCropCannotRequestExpansion() {
        val invalid = NormalizedCrop(Float.NaN, 0f, 1f, 1f)
        assertEquals(
            DisplayMode.TRACKPAD,
            resolveDisplayComposition(DisplayModePreferences(), invalid, true, true).mode,
        )
    }

    @Test fun disconnectForcesSafeComposition() {
        assertEquals(
            DisplayMode.TRACKPAD,
            resolveDisplayComposition(DisplayModePreferences(), bottomCrop, false, true).mode,
        )
    }

    @Test fun lowerSurfaceLossForcesSafeComposition() {
        assertEquals(
            DisplayMode.TRACKPAD,
            resolveDisplayComposition(DisplayModePreferences(), bottomCrop, true, false).mode,
        )
    }

    @Test fun reconnectCanRestorePreferredInterfaceMode() {
        val preferences = DisplayModePreferences()
        assertEquals(DisplayMode.TRACKPAD, resolveDisplayComposition(preferences, bottomCrop, false, false).mode)
        assertEquals(DisplayMode.INTERFACE, resolveDisplayComposition(preferences, bottomCrop, true, true).mode)
    }

    @Test fun editorOwnsFullFramePreviewRegardlessOfRuntimeMode() {
        val split = InterfaceSplit(0.75f)
        val target = resolvePresentationTarget(
            preferredMode = DisplayMode.TRACKPAD,
            savedSplit = InterfaceSplit(0.6f),
            editorSplit = split,
            connected = true,
            activeSurfaceReady = true,
        )
        assertEquals(PresentationOwner.EDITOR, target.owner)
        assertEquals(DisplayMode.INTERFACE, target.mode)
        assertEquals(NormalizedCrop.FullFrame, target.lowerCrop)
        assertEquals(split.interfaceCrop, target.upperCrop)
        assertFalse(target.runtimePanelRequested)
    }

    @Test fun splitViewWaitsForItsOwnSurfaceBeforeExpandingUpperScreen() {
        val target = resolvePresentationTarget(
            preferredMode = DisplayMode.INTERFACE,
            savedSplit = InterfaceSplit(0.75f),
            editorSplit = null,
            connected = true,
            activeSurfaceReady = false,
        )
        assertEquals(PresentationOwner.SPLIT_VIEW, target.owner)
        assertEquals(DisplayMode.TRACKPAD, target.mode)
        assertTrue(target.runtimePanelRequested)
    }

    @Test fun fullWidthBottomCropProducesTopGameplayRectangle() {
        val region = deriveUpperGameplayRegion(bottomCrop)
        assertEquals(UpperExpansionEdge.BOTTOM, region?.interfaceEdge)
        assertEquals(NormalizedCrop(0f, 0f, 1f, 0.75f), region?.crop)
    }

    @Test fun fullWidthTopCropIsNotASupportedSplit() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0f, 0f, 1f, 0.25f)))
    }

    @Test fun fullHeightLeftCropIsNotASupportedSplit() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0f, 0f, 0.25f, 1f)))
    }

    @Test fun fullHeightRightCropIsNotASupportedSplit() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0.75f, 0f, 1f, 1f)))
    }

    @Test fun floatingCropIsUnsupported() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0.2f, 0.2f, 0.8f, 0.8f)))
    }

    @Test fun partialWidthBottomCropIsUnsupported() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0.1f, 0.75f, 1f, 1f)))
    }

    @Test fun degenerateComplementIsRejected() {
        assertNull(deriveUpperGameplayRegion(NormalizedCrop(0f, 0.049f, 1f, 1f)))
    }

    @Test fun complementPixelConversionRemainsInBounds() {
        assertEquals(PixelCrop(0, 0, 320, 150), deriveUpperGameplayRegion(bottomCrop)?.crop?.toPixels(320, 200))
    }

    @Test fun singleTwoFingerTapResolvesToOneRightClickAtTimeout() {
        val resolver = TwoFingerDoubleTapResolver(300)
        assertEquals(TwoFingerTapResolution.WAIT_FOR_SECOND_TAP, resolver.onTwoFingerTap(1000))
        assertTrue(resolver.resolveTimeout(1000))
        assertFalse(resolver.resolveTimeout(1000))
    }

    @Test fun twoTwoFingerTapsToggleOnceWithoutRightClickTimeout() {
        val resolver = TwoFingerDoubleTapResolver(300)
        resolver.onTwoFingerTap(1000)
        assertEquals(TwoFingerTapResolution.TOGGLE_MODE, resolver.onTwoFingerTap(1200))
        assertFalse(resolver.resolveTimeout(1000))
    }

    @Test fun lateSecondTwoFingerTapStartsANewCandidate() {
        val resolver = TwoFingerDoubleTapResolver(300)
        resolver.onTwoFingerTap(1000)
        assertEquals(TwoFingerTapResolution.WAIT_FOR_SECOND_TAP, resolver.onTwoFingerTap(1400))
        assertTrue(resolver.resolveTimeout(1400))
    }

    @Test fun movementOrPinchCancellationResolvesPendingSingleTapOnce() {
        val resolver = TwoFingerDoubleTapResolver(300)
        resolver.onTwoFingerTap(1000)
        assertTrue(resolver.cancelAndResolveSingleTap())
        assertFalse(resolver.cancelAndResolveSingleTap())
    }

    @Test fun lifecycleResetClearsPendingToggleWithoutClick() {
        val resolver = TwoFingerDoubleTapResolver(300)
        resolver.onTwoFingerTap(1000)
        resolver.reset()
        assertFalse(resolver.resolveTimeout(1000))
        assertFalse(resolver.cancelAndResolveSingleTap())
    }

    @Test fun l2R2ChordTogglesOnceAndDoesNotForwardIndividualPresses() {
        val resolver = ControllerTriggerChordResolver(120)
        val left = resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1000)
        assertTrue(left.forward.isEmpty())
        val chord = resolver.onAxis(TriggerAxisValue(1, RIGHT_TRIGGER_AXIS_FLAG, 32767), 1100)
        assertTrue(chord.toggle)
        assertTrue(chord.forward.isEmpty())
        assertFalse(resolver.onAxis(TriggerAxisValue(1, RIGHT_TRIGGER_AXIS_FLAG, 32767), 1110).toggle)
    }

    @Test fun individualTriggerIsForwardedAfterBoundedChordWindow() {
        val resolver = ControllerTriggerChordResolver(120)
        val first = resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1000)
        assertEquals(1120L, first.scheduleTimeoutAt)
        val timeout = resolver.onTimeout(1120)
        assertEquals(listOf(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767)), timeout.forward)
        assertFalse(timeout.toggle)
    }

    @Test fun l2R2ChordRequiresBothReleasesBeforeAnotherToggle() {
        val resolver = ControllerTriggerChordResolver(120)
        resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1000)
        assertTrue(resolver.onAxis(TriggerAxisValue(1, RIGHT_TRIGGER_AXIS_FLAG, 32767), 1050).toggle)
        assertFalse(resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 0), 1060).toggle)
        assertFalse(resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1070).toggle)
        resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 0), 1080)
        resolver.onAxis(TriggerAxisValue(1, RIGHT_TRIGGER_AXIS_FLAG, 0), 1090)
        resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1200)
        assertTrue(resolver.onAxis(TriggerAxisValue(1, RIGHT_TRIGGER_AXIS_FLAG, 32767), 1250).toggle)
    }

    @Test fun triggerLifecycleResetClearsPendingChord() {
        val resolver = ControllerTriggerChordResolver(120)
        resolver.onAxis(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 32767), 1000)
        assertEquals(listOf(TriggerAxisValue(1, LEFT_TRIGGER_AXIS_FLAG, 0)), resolver.reset())
        assertTrue(resolver.onTimeout(1120).forward.isEmpty())
    }

    @Test fun interfaceHeightFollowsSplitAndTrackpadConsumesRemainder() {
        val smallerInterface = calculateLowerScreenLayout(
            1920, 1080, interfaceAspectRatio = 4f, interfaceVisible = true,
            mouseButtonsHeight = 68, utilityBarHeight = 56, minimumTrackpadHeight = 120,
        )!!
        val largerInterface = calculateLowerScreenLayout(
            1920, 1080, interfaceAspectRatio = 2.5f, interfaceVisible = true,
            mouseButtonsHeight = 68, utilityBarHeight = 56, minimumTrackpadHeight = 120,
        )!!
        assertTrue(largerInterface.interfaceHeight > smallerInterface.interfaceHeight)
        assertTrue(largerInterface.trackpadHeight < smallerInterface.trackpadHeight)
    }

    @Test fun lowerLayoutKeepsTrackpadAndBottomControlsInsideBounds() {
        val layout = calculateLowerScreenLayout(
            1920, 1080, interfaceAspectRatio = 1.4f, interfaceVisible = true,
            mouseButtonsHeight = 68, utilityBarHeight = 56, minimumTrackpadHeight = 120,
        )!!
        assertTrue(layout.trackpadHeight >= 120)
        assertTrue(layout.mouseButtonsTop >= layout.interfaceHeight + 120)
        assertTrue(layout.utilityBarTop >= layout.mouseButtonsTop + 68)
        assertEquals(1080, layout.bottom)
    }

    @Test fun trackpadModeGivesAllFlexibleHeightToTrackpad() {
        val layout = calculateLowerScreenLayout(
            1920, 1080, interfaceAspectRatio = 2.5f, interfaceVisible = false,
            mouseButtonsHeight = 68, utilityBarHeight = 56, minimumTrackpadHeight = 120,
        )!!
        assertEquals(0, layout.interfaceHeight)
        assertEquals(956, layout.trackpadHeight)
    }

    @Test fun staleUpperAcknowledgementsAreIgnored() {
        val gate = UpperPresentationGate()
        gate.begin(11, upperRequest())
        assertFalse(gate.acknowledge(ack(10, UpperPresentationResult.EXPANDED_APPLIED)))
        assertTrue(gate.acknowledge(ack(11, UpperPresentationResult.EXPANDED_APPLIED)))
    }

    @Test fun upperExpansionRejectionCanBeAcknowledgedWithoutInvalidatingLowerCrop() {
        val gate = UpperPresentationGate()
        gate.begin(12, upperRequest())
        assertTrue(gate.acknowledge(ack(12, UpperPresentationResult.EXPANDED_UNSUPPORTED_SHAPE)))
        assertTrue(bottomCrop.isValid())
    }

    @Test fun identicalModeReconciliationIsSuppressedUntilTargetChanges() {
        val gate = UpperPresentationGate()
        assertTrue(gate.begin(12, upperRequest()))
        assertFalse(gate.begin(13, upperRequest()))
        assertTrue(gate.begin(14, upperRequest().copy(mode = DisplayMode.TRACKPAD)))
    }

    @Test fun invalidStoredModeFallsBackToInterfaceSafely() {
        assertEquals(DisplayMode.INTERFACE, displayModeFromStoredValue("corrupt"))
        assertEquals(DisplayMode.TRACKPAD, displayModeFromStoredValue("TRACKPAD"))
    }

    @Test fun preferredModePersists() = runBlocking {
        val store = FakeDisplayModePreferencesStore()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = DisplayModePreferencesRepository(store, firstScope)
        first.save(DisplayModePreferences(DisplayMode.TRACKPAD))
        firstScope.cancel()
        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val restored = DisplayModePreferencesRepository(store, restoredScope)
        assertEquals(DisplayModePreferences(DisplayMode.TRACKPAD), restored.preferences.value)
        restoredScope.cancel()
    }

    private fun ack(generation: Long, result: UpperPresentationResult) =
        UpperPresentationAcknowledgement(result, generation, 2, "test")

    private fun upperRequest() = UpperPresentationRequest(DisplayMode.INTERFACE, bottomCrop, 2)
}

private class FakeDisplayModePreferencesStore : DisplayModePreferencesStore {
    private val persisted = MutableStateFlow(DisplayModePreferences())
    override val preferences: Flow<DisplayModePreferences> = persisted

    override suspend fun save(preferences: DisplayModePreferences) {
        persisted.value = preferences
    }
}
