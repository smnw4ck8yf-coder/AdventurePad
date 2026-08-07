package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Stable visual roles shared by gameplay, Companion, and Settings. */
object AdventurePadDesign {
    val background = Color(0xFF111417)
    val surface = Color(0xFF1A1F24)
    val surfaceRaised = Color(0xFF22282E)
    val surfacePressed = Color(0xFF303841)
    val outline = Color(0xFF46515B)
    val outlineStrong = Color(0xFF64717D)
    val primary = Color(0xFFD8B86A)
    val onPrimary = Color(0xFF211B0D)
    val textPrimary = Color(0xFFF2F3F5)
    val textSecondary = Color(0xFFADB5BD)
    val connected = Color(0xFF72C08A)
    val disconnected = Color(0xFFD08080)

    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val contentPadding = 16.dp
    val utilityTouchTarget = 56.dp
    val trackpadOverlayMinimumHeight = 56.dp
    val trackpadOverlayMaximumHeight = 88.dp
    val cornerSmall = 8.dp
    val cornerMedium = 12.dp
    val cornerLarge = 16.dp

    val smallShape = RoundedCornerShape(cornerSmall)
    val mediumShape = RoundedCornerShape(cornerMedium)
    val largeShape = RoundedCornerShape(cornerLarge)
    val subtleBorder = BorderStroke(1.dp, outline)
}

val AdventurePadShapes = Shapes(
    extraSmall = AdventurePadDesign.smallShape,
    small = AdventurePadDesign.smallShape,
    medium = AdventurePadDesign.mediumShape,
    large = AdventurePadDesign.largeShape,
    extraLarge = AdventurePadDesign.largeShape,
)
