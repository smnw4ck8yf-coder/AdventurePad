package com.jamesmoran.adventurepad

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import java.util.Locale

class TrackpadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val displayManager = getSystemService(DisplayManager::class.java)
        val trackpadDisplay = display?.let { displayManager.getDisplay(it.displayId) ?: it }

        setContent {
            AdventurePadTheme {
                TrackpadTouchTestScreen(trackpadDisplay)
            }
        }
    }
}

@Composable
private fun TrackpadTouchTestScreen(display: Display?) {
    var touchState by remember { mutableStateOf(TouchState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackpadBackground)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TRACKPAD TOUCH TEST",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Display ID: ${display?.displayId ?: "unavailable"}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        TouchSurface(
            touchState = touchState,
            onTouchStateChanged = { touchState = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
        )

        TouchDiagnostics(touchState)
    }
}

@Composable
private fun TouchSurface(
    touchState: TouchState,
    onTouchStateChanged: (TouchState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder)
            // This milestone visualizes local touch only; it deliberately performs no cursor injection.
            .pointerInput(Unit) {
                var currentState = touchState
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val nextState = handlePointerEvent(
                            event = event,
                            previousState = currentState,
                            surfaceWidth = size.width.toFloat(),
                            surfaceHeight = size.height.toFloat(),
                        )
                        currentState = nextState
                        onTouchStateChanged(nextState)
                        event.changes.forEach(PointerInputChange::consume)
                    }
                }
            },
    ) {
        if (touchState.markerVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = MarkerRadius.toPx()
                val markerCenter = Offset(
                    x = touchState.absoluteX.coerceIn(radius, (size.width - radius).coerceAtLeast(radius)),
                    y = touchState.absoluteY.coerceIn(radius, (size.height - radius).coerceAtLeast(radius)),
                )
                drawCircle(Color.White, radius = radius + MarkerOutlineWidth.toPx(), center = markerCenter)
                drawCircle(MarkerColor, radius = radius, center = markerCenter)
            }
        } else {
            Text(
                text = "Touch and drag anywhere in this surface",
                color = TouchSurfaceBorder,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun handlePointerEvent(
    event: PointerEvent,
    previousState: TouchState,
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val activePointerCount = event.changes.count { it.pressed }
    val action = when (event.type) {
        PointerEventType.Press -> if (previousState.pointerCount == 0) {
            TouchAction.DOWN
        } else {
            TouchAction.POINTER_DOWN
        }
        PointerEventType.Move -> TouchAction.MOVE
        PointerEventType.Release -> if (activePointerCount == 0) {
            TouchAction.UP
        } else {
            TouchAction.POINTER_UP
        }
        PointerEventType.Unknown -> if (
            previousState.trackedPointerId != null &&
            event.changes.isNotEmpty() &&
            event.changes.none { it.pressed }
        ) {
            TouchAction.CANCEL
        } else {
            null
        }
        else -> null
    }

    return when (action) {
        TouchAction.DOWN -> {
            val initialPointer = event.changes.firstOrNull { it.pressed } ?: return previousState
            val position = initialPointer.position.constrainTo(surfaceWidth, surfaceHeight)

            // A new gesture selects its first pointer and resets the baseline, so its initial delta is zero.
            TouchState(
                absoluteX = position.x,
                absoluteY = position.y,
                pointerCount = activePointerCount,
                trackedPointerId = initialPointer.id,
                action = TouchAction.DOWN,
                markerVisible = true,
            )
        }

        TouchAction.MOVE -> {
            val trackedPointer = event.findPressedPointer(previousState.trackedPointerId)
            if (trackedPointer == null) {
                previousState.copy(
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                )
            } else if (trackedPointer.id != previousState.trackedPointerId) {
                previousState.withResetBaseline(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            } else {
                previousState.withTrackedPosition(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    moveEventCount = (previousState.moveEventCount + 1)
                        .coerceAtMost(MaxMoveEventCount),
                )
            }
        }

        TouchAction.POINTER_DOWN -> {
            val trackedPointer = event.findPointer(previousState.trackedPointerId)
            if (trackedPointer == null) {
                previousState.copy(
                    pointerCount = activePointerCount,
                    action = TouchAction.POINTER_DOWN,
                )
            } else {
                // Additional fingers never replace the pointer selected at ACTION_DOWN.
                previousState.withTrackedPosition(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.POINTER_DOWN,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            }
        }

        TouchAction.POINTER_UP -> {
            val liftedPointerId = event.changes
                .firstOrNull { it.previousPressed && !it.pressed }
                ?.id
            if (liftedPointerId == previousState.trackedPointerId) {
                val replacement = event.changes.firstOrNull { it.pressed }
                if (replacement == null) {
                    previousState.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        trackedPointerId = null,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    // Reset the baseline on handoff so a different finger cannot create an artificial jump.
                    previousState.withResetBaseline(
                        pointer = replacement,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            } else {
                val trackedPointer = event.findPointer(previousState.trackedPointerId)
                if (trackedPointer == null) {
                    previousState.copy(
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    previousState.withTrackedPosition(
                        pointer = trackedPointer,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            }
        }

        TouchAction.UP -> {
            val finalPointer = event.findPointer(previousState.trackedPointerId)
            if (finalPointer == null) {
                previousState.copy(
                    pointerCount = 0,
                    action = TouchAction.UP,
                )
            } else {
                previousState.withTrackedPosition(
                    pointer = finalPointer,
                    pointerCount = 0,
                    action = TouchAction.UP,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            }
        }

        TouchAction.CANCEL -> TouchState(action = TouchAction.CANCEL)

        null -> previousState.copy(pointerCount = activePointerCount)
    }
}

private fun TouchState.withTrackedPosition(
    pointer: PointerInputChange,
    pointerCount: Int,
    action: TouchAction,
    surfaceWidth: Float,
    surfaceHeight: Float,
    moveEventCount: Int = this.moveEventCount,
): TouchState {
    val position = pointer.position.constrainTo(surfaceWidth, surfaceHeight)
    return copy(
        absoluteX = position.x,
        absoluteY = position.y,
        // Absolute values locate the finger in the surface; deltas compare consecutive events.
        deltaX = position.x - absoluteX,
        deltaY = position.y - absoluteY,
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
        moveEventCount = moveEventCount,
        markerVisible = true,
    )
}

private fun TouchState.withResetBaseline(
    pointer: PointerInputChange,
    pointerCount: Int,
    action: TouchAction,
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val position = pointer.position.constrainTo(surfaceWidth, surfaceHeight)
    return copy(
        absoluteX = position.x,
        absoluteY = position.y,
        deltaX = 0f,
        deltaY = 0f,
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
        markerVisible = true,
    )
}

private fun PointerEvent.findPointer(pointerId: PointerId?): PointerInputChange? =
    pointerId?.let { id -> changes.firstOrNull { it.id == id } }

private fun PointerEvent.findPressedPointer(pointerId: PointerId?): PointerInputChange? =
    findPointer(pointerId)?.takeIf { it.pressed } ?: changes.firstOrNull { it.pressed }

private fun Offset.constrainTo(surfaceWidth: Float, surfaceHeight: Float) = Offset(
    x = x.coerceIn(0f, surfaceWidth.coerceAtLeast(0f)),
    y = y.coerceIn(0f, surfaceHeight.coerceAtLeast(0f)),
)

@Composable
private fun TouchDiagnostics(touchState: TouchState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiagnosticRow(
            "Absolute X: ${touchState.absoluteX.asCoordinate()}",
            "Absolute Y: ${touchState.absoluteY.asCoordinate()}",
            "Pointers: ${touchState.pointerCount}",
            "Tracked ID: ${touchState.trackedPointerId?.value ?: "none"}",
        )
        DiagnosticRow(
            "Delta X: ${touchState.deltaX.asCoordinate()}",
            "Delta Y: ${touchState.deltaY.asCoordinate()}",
            "Action: ${touchState.action.label}",
            "Move events: ${touchState.moveEventCount}",
        )
    }
}

@Composable
private fun DiagnosticRow(vararg values: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        values.forEach { value ->
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun Float.asCoordinate(): String = String.format(Locale.US, "%.1f", this)

private data class TouchState(
    val absoluteX: Float = 0f,
    val absoluteY: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val pointerCount: Int = 0,
    val trackedPointerId: PointerId? = null,
    val action: TouchAction = TouchAction.CANCEL,
    val moveEventCount: Int = 0,
    val markerVisible: Boolean = false,
)

private enum class TouchAction(val label: String) {
    DOWN("DOWN"),
    MOVE("MOVE"),
    UP("UP"),
    CANCEL("CANCEL"),
    POINTER_DOWN("POINTER_DOWN"),
    POINTER_UP("POINTER_UP"),
}

private val TrackpadBackground = Color(0xFF6B1D2A)
private val TouchSurfaceBackground = Color(0xFF2D0B13)
private val TouchSurfaceBorder = Color(0xFFFFB3C1)
private val MarkerColor = Color(0xFF00E5FF)
private val MarkerRadius = 16.dp
private val MarkerOutlineWidth = 3.dp
private const val MaxMoveEventCount = 999_999
