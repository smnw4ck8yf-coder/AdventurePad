package com.jamesmoran.adventurepad

internal data class GameplaySafeCenterResult(
    val nonTransparentPixels: Int,
    val allowedNonTransparentPixels: Int,
) {
    val isValid: Boolean get() = nonTransparentPixels <= allowedNonTransparentPixels
}

/** Applies a deliberately tolerant alpha budget to the fixed gameplay-safe centre. */
internal object GameplaySafeCenterValidator {
    fun validate(
        center: SkinGameplaySafeCenter,
        alphaAt: (x: Int, y: Int) -> Int,
    ): GameplaySafeCenterResult {
        var nonTransparentPixels = 0
        for (y in center.y until center.y + center.height) {
            for (x in center.x until center.x + center.width) {
                if (alphaAt(x, y) > center.ignoredAlphaAtOrBelow) nonTransparentPixels++
            }
        }
        val allowed = (center.width.toLong() * center.height * center.maxNonTransparentFraction)
            .toInt()
        return GameplaySafeCenterResult(nonTransparentPixels, allowed)
    }
}
