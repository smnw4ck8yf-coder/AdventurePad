package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorCropTest {
    @Test fun identicalCropReconciliationIsSingleFlight() {
        val gate = MirrorCropApplicationGate()
        val request = MirrorCropRequest(NormalizedCrop(0f, 0.75f, 1f, 1f), 8)
        assertTrue(gate.begin(request))
        assertFalse(gate.begin(request))
    }

    @Test fun changedCropOrGeometryCanBeAppliedImmediately() {
        val gate = MirrorCropApplicationGate()
        assertTrue(gate.begin(MirrorCropRequest(NormalizedCrop(0f, 0.75f, 1f, 1f), 8)))
        assertTrue(gate.begin(MirrorCropRequest(NormalizedCrop(0f, 0.7f, 1f, 1f), 8)))
        assertTrue(gate.begin(MirrorCropRequest(NormalizedCrop(0f, 0.7f, 1f, 1f), 9)))
    }

    @Test fun savedCropIsRestoredOnceForEveryMirrorHostLifetime() {
        val gate = MirrorCropApplicationGate()
        val savedCrop = MirrorCropRequest(NormalizedCrop(0f, 0.75f, 1f, 1f), 8)

        repeat(100) {
            gate.invalidate()
            assertTrue(gate.begin(savedCrop))
            assertFalse(gate.begin(savedCrop))
        }
    }

    private val geometry = MirrorSourceGeometry(320, 200, rendererCapability = 1, generation = 7)

    @Test fun splitDerivesComplementaryFullWidthRegions() {
        val split = InterfaceSplit(0.75f)
        assertEquals(NormalizedCrop(0f, 0f, 1f, 0.75f), split.upperCrop)
        assertEquals(NormalizedCrop(0f, 0.75f, 1f, 1f), split.interfaceCrop)
        assertEquals(split.upperCrop.bottom, split.interfaceCrop.top, 0f)
    }

    @Test fun splitRegionsCoverEverySourcePixelExactlyOnce() {
        val split = InterfaceSplit(0.753f).snappedTo(geometry.height)
        val upper = split.upperCrop.toPixels(geometry.width, geometry.height)!!
        val lower = split.interfaceCrop.toPixels(geometry.width, geometry.height)!!
        assertEquals(0, upper.top)
        assertEquals(upper.bottom, lower.top)
        assertEquals(geometry.height, lower.bottom)
        assertEquals(geometry.width, upper.right - upper.left)
        assertEquals(geometry.width, lower.right - lower.left)
    }

    @Test fun editorOnlyChangesVerticalSplitAndClampsToValidBounds() {
        val editor = CropEditorModel.create(InterfaceSplit(0.75f), geometry)
        assertEquals(MIN_SPLIT_RATIO, editor.withSplitRatio(-10f).split.ratio, 0.0001f)
        assertEquals(MAX_SPLIT_RATIO, editor.withSplitRatio(10f).split.ratio, 0.0001f)
        assertEquals(0f, editor.crop.left, 0f)
        assertEquals(1f, editor.crop.right, 0f)
        assertEquals(1f, editor.crop.bottom, 0f)
    }

    @Test fun splitSnapsToAStableSourcePixelBoundary() {
        val split = InterfaceSplit(0.753f).snappedTo(200)
        assertEquals(151f / 200f, split.ratio, 0f)
        assertEquals(split, split.snappedTo(200))
    }

    @Test fun legacyRectangleMigratesFromItsTopBoundaryOnly() {
        val split = InterfaceSplit.fromLegacyCrop(NormalizedCrop(0.2f, 0.62f, 0.8f, 0.9f))
        assertEquals(0.62f, split.ratio, 0f)
        assertEquals(NormalizedCrop(0f, 0.62f, 1f, 1f), split.interfaceCrop)
    }

    @Test fun savePersistsEditedSplitAndExitsConfiguration() {
        val gate = CropSaveGate()
        val edited = InterfaceSplit(0.7f)
        assertTrue(gate.begin(10, edited))
        assertNull(gate.acknowledge(CropAcknowledgement(CropAcknowledgementResult.APPLIED, 9, 2, "old")))
        val acknowledged = gate.acknowledge(
            CropAcknowledgement(CropAcknowledgementResult.APPLIED, 10, 2, "ok"),
        )!!
        val completion = saveSplitEditor(acknowledged)
        assertEquals(edited, completion.split)
        assertTrue(completion.shouldPersist)
        assertFalse(completion.editorVisible)
    }

    @Test fun rejectedSaveDoesNotPersistSplit() {
        val gate = CropSaveGate()
        assertTrue(gate.begin(10, InterfaceSplit(0.7f)))
        assertNull(gate.acknowledge(CropAcknowledgement(CropAcknowledgementResult.REJECTED, 10, 2, "no")))
        assertNull(gate.pendingGeneration)
    }

    @Test fun cancelRestoresOriginalSplitWithoutPersistingAndExitsConfiguration() {
        val saved = InterfaceSplit(0.72f)
        val transaction = CropEditTransaction(saved)
        transaction.update(InterfaceSplit(0.4f))
        val completion = cancelSplitEditor(transaction, InterfaceSplit.Default)
        assertEquals(saved, completion.split)
        assertFalse(completion.shouldPersist)
        assertFalse(completion.editorVisible)
    }

    @Test fun editingControlsOnlyShowWhileConfigurationIsActive() {
        val editor = CropEditorModel.create(InterfaceSplit(0.75f), geometry)
        assertTrue(shouldShowSplitEditorControls(editor))
        assertFalse(shouldShowSplitEditorControls(null))
    }

    @Test fun profileCompatibilityRequiresConfirmedCurrentGeometry() {
        val profile = MirrorCropProfile(
            split = InterfaceSplit(0.75f),
            sourceWidth = 320,
            sourceHeight = 200,
            sourceAspectRatio = 1.6f,
            confirmed = true,
            requiresReview = false,
        )
        assertTrue(profile.isCompatibleWith(geometry))
        assertFalse(profile.isCompatibleWith(geometry.copy(width = 640, height = 400)))
    }
}
