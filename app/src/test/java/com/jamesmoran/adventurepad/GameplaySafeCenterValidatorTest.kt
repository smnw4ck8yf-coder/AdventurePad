package com.jamesmoran.adventurepad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplaySafeCenterValidatorTest {
    private val center = SkinGameplaySafeCenter(
        x = 320, y = 0, width = 1280, height = 1080,
        ignoredAlphaAtOrBelow = 32, maxNonTransparentFraction = 0.005,
    )

    @Test
    fun opaqueSidePanelsAndTransparentCenterPass() {
        val result = validate { x, _ -> if (x < 320 || x >= 1600) 255 else 0 }
        assertTrue(result.isValid)
    }

    @Test
    fun antialiasingAndSparseEdgeDetailPass() {
        val result = validate { x, y ->
            when {
                x < 320 || x >= 1600 -> 255
                x < 325 -> 96 // Five full-height feather/detail columns fit the tolerance budget.
                y % 240 == 0 && x < 420 -> 180
                else -> 24 // Low-alpha shadow is deliberately ignored.
            }
        }
        assertTrue(result.isValid)
    }

    @Test
    fun substantialOpaqueIntrusionIntoCenterFails() {
        val result = validate { x, y -> if (x in 800 until 1000 && y in 300 until 700) 255 else 0 }
        assertFalse(result.isValid)
    }

    private fun validate(alphaAt: (Int, Int) -> Int) =
        GameplaySafeCenterValidator.validate(center, alphaAt)
}
