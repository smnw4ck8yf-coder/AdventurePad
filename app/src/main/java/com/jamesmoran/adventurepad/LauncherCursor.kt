package com.jamesmoran.adventurepad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens

private val LauncherCursorRadius = 8.dp
private val LauncherCursorOutlineWidth = 2.dp

/** Non-interactive cursor overlay owned only by the resumed top-display launcher. */
@Composable
internal fun AdventurePadLauncherCursor(
    ownsTopScreen: Boolean,
    onStateChanged: (LauncherCursorState, Boolean) -> Unit = { _, _ -> },
) {
    var cursorState by remember { mutableStateOf(LauncherCursorState()) }
    val cursorRadius = with(LocalDensity.current) { LauncherCursorRadius.toPx() }
    val cursorColor = AdventurePadThemeTokens.components.topCursor
    val cursorOutlineColor = AdventurePadThemeTokens.components.topCursorOutline

    // Keep following the existing cross-display pointer pipeline while Advanced ScummVM is on top.
    // Ownership controls drawing, not lower-display focus or whether a fresh movement occurred.
    DisposableEffect(Unit) {
        val subscription = CursorDeltaCoordinator.subscribe { delta ->
            cursorState = cursorState.moveBy(delta.dx, delta.dy)
            onStateChanged(cursorState, true)
        }
        onDispose(subscription::cancel)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .onSizeChanged { size ->
                cursorState = cursorState.withBounds(
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    radius = cursorRadius,
                )
                onStateChanged(cursorState, false)
            },
    ) {
        if (!cursorState.isVisible(ownsTopScreen)) return@Canvas
        val center = Offset(cursorState.x, cursorState.y)
        drawCircle(
            color = cursorOutlineColor,
            radius = cursorRadius + LauncherCursorOutlineWidth.toPx(),
            center = center,
        )
        drawCircle(color = cursorColor, radius = cursorRadius, center = center)
    }
}

internal data class LauncherCursorState(
    val x: Float = 0f,
    val y: Float = 0f,
    val minimumX: Float = 0f,
    val maximumX: Float = 0f,
    val minimumY: Float = 0f,
    val maximumY: Float = 0f,
    val initialized: Boolean = false,
) {
    fun isVisible(ownsTopScreen: Boolean): Boolean = ownsTopScreen && initialized

    fun withBounds(width: Float, height: Float, radius: Float): LauncherCursorState {
        val boundedWidth = width.coerceAtLeast(0f)
        val boundedHeight = height.coerceAtLeast(0f)
        val horizontalInset = radius.coerceIn(0f, boundedWidth / 2f)
        val verticalInset = radius.coerceIn(0f, boundedHeight / 2f)
        val newMinimumX = horizontalInset
        val newMaximumX = (boundedWidth - horizontalInset).coerceAtLeast(newMinimumX)
        val newMinimumY = verticalInset
        val newMaximumY = (boundedHeight - verticalInset).coerceAtLeast(newMinimumY)
        return if (initialized) {
            copy(
                x = x.coerceIn(newMinimumX, newMaximumX),
                y = y.coerceIn(newMinimumY, newMaximumY),
                minimumX = newMinimumX,
                maximumX = newMaximumX,
                minimumY = newMinimumY,
                maximumY = newMaximumY,
            )
        } else {
            copy(
                x = boundedWidth / 2f,
                y = boundedHeight / 2f,
                minimumX = newMinimumX,
                maximumX = newMaximumX,
                minimumY = newMinimumY,
                maximumY = newMaximumY,
                initialized = boundedWidth > 0f && boundedHeight > 0f,
            )
        }
    }

    fun moveBy(dx: Float, dy: Float): LauncherCursorState {
        if (!initialized || !dx.isFinite() || !dy.isFinite()) return this
        return copy(
            x = (x + dx).coerceIn(minimumX, maximumX),
            y = (y + dy).coerceIn(minimumY, maximumY),
        )
    }
}
