package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Test

class NineSliceGeometryTest {
    private val insets = SkinInsets(left = 64, top = 64, right = 64, bottom = 64)
    private val activeArtworkBounds = PixelRect(0, 37, 1227, 325)
    private val activeContentBounds = PixelRect(20, 56, 1205, 304)

    @Test
    fun activeArtworkConsumesCompleteThorInterfaceBoundary() {
        val patches = patches(height = 246).associateBy { it.patch }

        assertEquals(PixelRect(0, 0, 64, 27), patches.getValue(BorderPatch.TOP_LEFT).destination)
        assertEquals(PixelRect(64, 0, 1189, 27), patches.getValue(BorderPatch.TOP).destination)
        assertEquals(PixelRect(1189, 0, 1240, 27), patches.getValue(BorderPatch.TOP_RIGHT).destination)
        assertEquals(PixelRect(0, 27, 64, 217), patches.getValue(BorderPatch.LEFT).destination)
        assertEquals(PixelRect(1189, 27, 1240, 217), patches.getValue(BorderPatch.RIGHT).destination)
        assertEquals(PixelRect(0, 217, 64, 246), patches.getValue(BorderPatch.BOTTOM_LEFT).destination)
        assertEquals(PixelRect(64, 217, 1189, 246), patches.getValue(BorderPatch.BOTTOM).destination)
        assertEquals(PixelRect(1189, 217, 1240, 246), patches.getValue(BorderPatch.BOTTOM_RIGHT).destination)
        assertEquals(8, patches.size)
        val opening = calculateBorderContentRect(1240, 246, patches.values.toList())
        assertEquals(PixelRect(64, 27, 1189, 217), opening)
        val visibleOpening = calculateBorderContentRect(
            1240,
            246,
            patches.values.toList(),
            activeContentBounds,
        )
        assertEquals(PixelRect(20, 19, 1218, 225), visibleOpening)
    }

    @Test
    fun aDifferentRuntimeHeightMovesOnlyTheLowerPatches() {
        val patches = patches(height = 360).associateBy { it.patch }

        assertEquals(PixelRect(0, 0, 64, 27), patches.getValue(BorderPatch.TOP_LEFT).destination)
        assertEquals(PixelRect(0, 27, 64, 331), patches.getValue(BorderPatch.LEFT).destination)
        assertEquals(PixelRect(64, 331, 1189, 360), patches.getValue(BorderPatch.BOTTOM).destination)
        assertEquals(PixelRect(1189, 331, 1240, 360), patches.getValue(BorderPatch.BOTTOM_RIGHT).destination)
        val opening = calculateBorderContentRect(1240, 360, patches.values.toList())
        assertEquals(PixelRect(64, 27, 1189, 331), opening)
        val visibleOpening = calculateBorderContentRect(
            1240,
            360,
            patches.values.toList(),
            activeContentBounds,
        )
        assertEquals(PixelRect(20, 19, 1218, 339), visibleOpening)
    }

    @Test
    fun thorRuntimeOpeningTracksPaintedRailsRatherThanSliceBoundaries() {
        val runtimePatches = patches(height = 251)

        val opening = calculateBorderContentRect(
            destinationWidth = 1240,
            destinationHeight = 251,
            patches = runtimePatches,
            sourceContentBounds = activeContentBounds,
        )

        assertEquals(PixelRect(20, 19, 1218, 230), opening)
        assertEquals(1198, opening.width)
        assertEquals(211, opening.height)
    }

    @Test
    fun transparentPaddingInsideSliceInsetsDoesNotBecomeAContentGap() {
        val visiblePixels = buildSet {
            for (y in 64 until 296) {
                for (x in 0 until 20) add(PixelPointKey(x, y))
                for (x in 1205 until 1227) add(PixelPointKey(x, y))
            }
            for (x in 64 until 1176) {
                for (y in 37 until 56) add(PixelPointKey(x, y))
                for (y in 304 until 325) add(PixelPointKey(x, y))
            }
        }

        val content = calculateFrameSourceContentBounds(
            sourceWidth = 1240,
            sourceHeight = 360,
            insets = insets,
            visibleBounds = activeArtworkBounds,
            isVisible = { x, y -> PixelPointKey(x, y) in visiblePixels },
        )

        assertEquals(activeContentBounds, content)
    }

    @Test
    fun contentTransformRoundTripsForwardAndInverseCoordinates() {
        val transform = PanelContentTransform(
            content = PixelRect(64, 27, 1189, 217),
            interfaceWidth = 1240,
            interfaceHeight = 246,
        )
        val local = PixelPoint(419.25f, 83.5f)

        val outer = transform.localToOuter(local)

        assertEquals(PixelPoint(483.25f, 110.5f), outer)
        assertEquals(local, transform.outerToLocal(outer))

        val originalInterfacePoint = PixelPoint(310f, 61.5f)
        val displayedPoint = transform.interfaceToOuter(originalInterfacePoint)
        assertEquals(345.25f, displayedPoint.x, 0.001f)
        assertEquals(74.5f, displayedPoint.y, 0.001f)
        val restoredInterfacePoint = transform.outerToInterface(displayedPoint)
        assertEquals(originalInterfacePoint.x, restoredInterfacePoint.x, 0.001f)
        assertEquals(originalInterfacePoint.y, restoredInterfacePoint.y, 0.001f)
    }

    @Test
    fun finalThorTransformKeepsFarRightAndBottomCoordinatesInsideTheSourceSurface() {
        val transform = PanelContentTransform(
            content = PixelRect(20, 19, 1218, 230),
            interfaceWidth = 1240,
            interfaceHeight = 251,
        )

        val topRightCellTap = transform.outerToInterface(PixelPoint(1160f, 80f))
        val bottomRightCellTap = transform.outerToInterface(PixelPoint(1160f, 180f))

        assertEquals(1179.9667f, topRightCellTap.x, 0.001f)
        assertEquals(72.5640f, topRightCellTap.y, 0.001f)
        assertEquals(1179.9667f, bottomRightCellTap.x, 0.001f)
        assertEquals(191.5213f, bottomRightCellTap.y, 0.001f)
        val restoredTopRight = transform.interfaceToOuter(topRightCellTap)
        val restoredBottomRight = transform.interfaceToOuter(bottomRightCellTap)
        assertEquals(1160f, restoredTopRight.x, 0.001f)
        assertEquals(80f, restoredTopRight.y, 0.001f)
        assertEquals(1160f, restoredBottomRight.x, 0.001f)
        assertEquals(180f, restoredBottomRight.y, 0.001f)
    }

    @Test
    fun shortPanelsUniformlyScaleCornersSoTheyCannotOverlap() {
        val patches = calculateBorderNineSlicePatches(
            destinationWidth = 80,
            destinationHeight = 40,
            sourceWidth = 1240,
            sourceHeight = 360,
            insets = insets,
        ).associateBy { it.patch }

        assertEquals(PixelRect(0, 0, 20, 20), patches.getValue(BorderPatch.TOP_LEFT).destination)
        assertEquals(PixelRect(60, 0, 80, 20), patches.getValue(BorderPatch.TOP_RIGHT).destination)
        assertEquals(PixelRect(0, 20, 20, 40), patches.getValue(BorderPatch.BOTTOM_LEFT).destination)
        assertEquals(PixelRect(60, 20, 80, 40), patches.getValue(BorderPatch.BOTTOM_RIGHT).destination)
        assertEquals(0, patches.getValue(BorderPatch.LEFT).destination.height)
        assertEquals(PixelRect(20, 0, 60, 20), patches.getValue(BorderPatch.TOP).destination)
    }

    private fun patches(height: Int): List<NineSlicePatch> = calculateBorderNineSlicePatches(
        destinationWidth = 1240,
        destinationHeight = height,
        sourceWidth = 1240,
        sourceHeight = 360,
        insets = insets,
        sourceOuterBounds = activeArtworkBounds,
    )

    private data class PixelPointKey(val x: Int, val y: Int)
}
