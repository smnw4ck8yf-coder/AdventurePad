package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowerPanelGeometryTest {
    private val crop = NormalizedCrop(0f, 0.75f, 1f, 1f)

    @Test fun topLeftMapsToCropTopLeft() {
        val geometry = lowerPanelGeometry(800, 125, crop, 320, 200, SourceOrientation.NORMAL)!!
        assertEquals(SourcePoint(0, 150), geometry.mapTouch(0f, 0f))
    }

    @Test fun bottomRightMapsInBounds() {
        val geometry = lowerPanelGeometry(800, 125, crop, 320, 200, SourceOrientation.NORMAL)!!
        assertEquals(SourcePoint(319, 199), geometry.mapTouch(799.999f, 124.999f))
    }

    @Test fun centerMapsToCropCenter() {
        val geometry = lowerPanelGeometry(800, 125, crop, 320, 200, SourceOrientation.NORMAL)!!
        assertEquals(SourcePoint(160, 175), geometry.mapTouch(400f, 62.5f))
    }

    @Test fun sourceCursorUsesTheInverseTouchTransform() {
        val geometry = lowerPanelGeometry(800, 125, crop, 320, 200, SourceOrientation.NORMAL)!!
        assertEquals(PanelPoint(400f, 62.5f), geometry.mapSource(SourcePoint(160, 175)))
        assertNull(geometry.mapSource(SourcePoint(160, 149)))

        val rotation90 = lowerPanelGeometry(125, 800, crop, 320, 200, SourceOrientation.ROTATE_90)!!
        assertEquals(PanelPoint(62.5f, 400f), rotation90.mapSource(SourcePoint(160, 175)))
    }

    @Test fun destinationAlwaysFillsSuppliedSurfaceWithoutPadding() {
        val geometry = lowerPanelGeometry(800, 300, crop, 320, 200, SourceOrientation.NORMAL)!!

        assertEquals(FloatRect(0f, 0f, 800f, 300f), geometry.destination)
        assertEquals(SourcePoint(0, 150), geometry.mapTouch(0f, 0f))
        assertEquals(SourcePoint(319, 199), geometry.mapTouch(799.999f, 299.999f))
    }

    @Test fun destinationStretchDoesNotChangeSourceCrop() {
        val aspectPreserved = lowerPanelGeometry(
            800, 125, crop, 320, 200, SourceOrientation.NORMAL,
        )!!
        val stretched = lowerPanelGeometry(
            800, 169, crop, 320, 200, SourceOrientation.NORMAL,
        )!!

        assertEquals(crop, aspectPreserved.crop)
        assertEquals(crop, stretched.crop)
        assertEquals(800f, stretched.destination.width, 0f)
        assertTrue(stretched.destination.height > aspectPreserved.destination.height)
        assertEquals(169f, stretched.destination.height, 0f)
        assertEquals(0f, stretched.destination.top, 0f)
        assertEquals(169f, stretched.destination.bottom, 0f)
        assertNull(stretched.mapSource(SourcePoint(160, 149)))
        assertEquals(150, stretched.mapTouch(400f, 0f)?.y)

        val xScale = stretched.destination.width / (crop.width * 320)
        val yScale = stretched.destination.height / (crop.height * 200)
        assertTrue(xScale != yScale)
    }

    @Test fun stretchedTouchAndCrosshairTransformsRemainInverse() {
        val geometry = lowerPanelGeometry(
            800, 300, crop, 320, 200, SourceOrientation.NORMAL,
        )!!
        val center = geometry.mapSource(SourcePoint(160, 175))!!

        assertEquals(SourcePoint(160, 175), geometry.mapTouch(center.x, center.y))
        assertEquals(400f, center.x, 0f)
        assertEquals(150f, center.y, 0f)
    }

    @Test fun verticalStretchClampsToAvailableHeightWithoutClipping() {
        val geometry = lowerPanelGeometry(
            800, 130, crop, 320, 200, SourceOrientation.NORMAL,
        )!!

        assertEquals(800f, geometry.destination.width, 0f)
        assertEquals(130f, geometry.destination.height, 0f)
        assertEquals(0f, geometry.destination.top, 0f)
        assertEquals(130f, geometry.destination.bottom, 0f)
    }

    @Test fun rotatedCornersUndoRendererOrientation() {
        val rotation90 = lowerPanelGeometry(125, 800, crop, 320, 200, SourceOrientation.ROTATE_90)!!
        assertEquals(SourcePoint(0, 199), rotation90.mapTouch(0f, 0f))
        assertEquals(SourcePoint(319, 150), rotation90.mapTouch(124.999f, 799.999f))
        val rotation180 = lowerPanelGeometry(800, 125, crop, 320, 200, SourceOrientation.ROTATE_180)!!
        assertEquals(SourcePoint(319, 199), rotation180.mapTouch(0f, 0f))
    }

    @Test fun cancelAndMultiTouchNeverProduceAnUpClick() {
        val tracker = LowerPanelTouchTracker(8f)
        assertEquals(AbsoluteSourcePointerAction.DOWN, tracker.begin(1, 10f, 10f, SourcePoint(4, 4)))
        assertEquals(AbsoluteSourcePointerAction.CANCEL, tracker.additionalPointer())
        assertNull(tracker.end(1, SourcePoint(4, 4)))
        assertEquals(AbsoluteSourcePointerAction.DOWN, tracker.begin(2, 10f, 10f, SourcePoint(4, 4)))
        tracker.move(2, 30f, 10f, SourcePoint(8, 4))
        assertEquals(AbsoluteSourcePointerAction.CANCEL, tracker.end(2, SourcePoint(8, 4)))
    }

}
