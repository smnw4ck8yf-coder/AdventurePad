package com.jamesmoran.adventurepad

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import java.util.Locale

class TrackpadActivity : ComponentActivity() {
    private var lifecycleEvent by mutableStateOf("INITIALIZING")
    private var lastLaunchResult by mutableStateOf("Waiting for launch details.")
    private var receivedIntentFlags by mutableStateOf(0)
    private var currentDisplayId by mutableStateOf(Display.INVALID_DISPLAY)
    private var controllerLeftButtonDown = false
    private var controllerRightButtonDown = false
    private val forwardedGamepadKeysDown = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receivedIntentFlags = intent.flags
        currentDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Launched because: $it" }
            ?: "TrackpadActivity created without a launch reason."
        recordLifecycle("CREATED")
        enableEdgeToEdge()

        val displayManager = getSystemService(DisplayManager::class.java)
        val trackpadDisplay = display?.let { displayManager.getDisplay(it.displayId) ?: it }

        setContent {
            AdventurePadTheme {
                TrackpadTouchTestScreen(
                    display = displayManager.getDisplay(currentDisplayId) ?: trackpadDisplay,
                    diagnostics = runtimeDiagnostics(),
                    onRestoreBothScreens = ::restoreBothScreens,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ScummVMInputClient.bind(this)
        recordLifecycle("STARTED")
    }

    override fun onResume() {
        super.onResume()
        recordLifecycle("RESUMED")
    }

    override fun onPause() {
        recordLifecycle("PAUSED")
        super.onPause()
    }

    override fun onStop() {
        recordLifecycle("STOPPED")
        releaseControllerButtons()
        releaseForwardedGamepadKeys()
        ScummVMInputClient.releaseJoystickAxes()
        ScummVMInputClient.unbind()
        super.onStop()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            event.actionMasked == MotionEvent.ACTION_MOVE &&
            ScummVMInputClient.sendJoystickMotion(event)
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mouseButton = when {
            event.keyCode == KeyEvent.KEYCODE_BUTTON_A -> ScummVMMouseButton.LEFT
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B -> ScummVMMouseButton.RIGHT
            event.keyCode == KeyEvent.KEYCODE_BACK && event.isFromGameController() -> {
                ScummVMMouseButton.RIGHT
            }
            else -> null
        }

        if (mouseButton != null && forwardMouseButton(event, mouseButton)) {
            return true
        }
        if (event.isFromGameController() && forwardGamepadKey(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        recordLifecycle("DESTROYED")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedIntentFlags = intent.flags
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Received launch request: $it" }
            ?: "Received a new intent without a launch reason."
        recordLifecycle("NEW_INTENT")
    }

    private fun restoreBothScreens() {
        lastLaunchResult = "Restore in progress…"
        lastLaunchResult = DualDisplayCoordinator.restoreBoth(this).message
    }

    private fun markControllerButtonDown(button: ScummVMMouseButton): Boolean = when (button) {
        ScummVMMouseButton.LEFT -> (!controllerLeftButtonDown).also {
            controllerLeftButtonDown = true
        }
        ScummVMMouseButton.RIGHT -> (!controllerRightButtonDown).also {
            controllerRightButtonDown = true
        }
    }

    private fun markControllerButtonUp(button: ScummVMMouseButton): Boolean = when (button) {
        ScummVMMouseButton.LEFT -> controllerLeftButtonDown.also {
            controllerLeftButtonDown = false
        }
        ScummVMMouseButton.RIGHT -> controllerRightButtonDown.also {
            controllerRightButtonDown = false
        }
    }

    private fun releaseControllerButtons() {
        if (markControllerButtonUp(ScummVMMouseButton.LEFT)) {
            ScummVMInputClient.sendButtonEvent(ScummVMButtonEvent.LEFT_BUTTON_UP)
        }
        if (markControllerButtonUp(ScummVMMouseButton.RIGHT)) {
            ScummVMInputClient.sendButtonEvent(ScummVMButtonEvent.RIGHT_BUTTON_UP)
        }
    }

    private fun forwardMouseButton(event: KeyEvent, button: ScummVMMouseButton): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (isControllerButtonDown(button)) {
                    true
                } else if (event.repeatCount == 0 &&
                    ScummVMInputClient.sendButtonEvent(button.downEvent)
                ) {
                    markControllerButtonDown(button)
                    Log.i(AdventurePadBridgeTag, "Button consumed: ${button.name} DOWN")
                    true
                } else {
                    false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!isControllerButtonDown(button)) {
                    false
                } else {
                    markControllerButtonUp(button)
                    ScummVMInputClient.sendButtonEvent(button.upEvent)
                    Log.i(AdventurePadBridgeTag, "Button consumed: ${button.name} UP")
                    true
                }
            }
            else -> false
        }
    }

    private fun forwardGamepadKey(event: KeyEvent): Boolean {
        if (!ScummVMInputClient.isForwardedGamepadKey(event.keyCode)) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.keyCode in forwardedGamepadKeysDown) {
                    true
                } else if (event.repeatCount == 0 &&
                    ScummVMInputClient.sendGamepadKeyEvent(event.action, event.keyCode)
                ) {
                    forwardedGamepadKeysDown += event.keyCode
                    true
                } else {
                    false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!forwardedGamepadKeysDown.remove(event.keyCode)) {
                    false
                } else {
                    ScummVMInputClient.sendGamepadKeyEvent(event.action, event.keyCode)
                    true
                }
            }
            else -> false
        }
    }

    private fun releaseForwardedGamepadKeys() {
        forwardedGamepadKeysDown.forEach { keyCode ->
            ScummVMInputClient.sendGamepadKeyEvent(KeyEvent.ACTION_UP, keyCode)
        }
        forwardedGamepadKeysDown.clear()
    }

    private fun isControllerButtonDown(button: ScummVMMouseButton): Boolean = when (button) {
        ScummVMMouseButton.LEFT -> controllerLeftButtonDown
        ScummVMMouseButton.RIGHT -> controllerRightButtonDown
    }

    private fun runtimeDiagnostics() = ActivityRuntimeDiagnostics(
        displayId = currentDisplayId,
        taskId = taskId,
        isTaskRoot = isTaskRoot,
        lifecycleEvent = lifecycleEvent,
        intentFlags = receivedIntentFlags,
        lastResult = lastLaunchResult,
    )

    private fun recordLifecycle(event: String) {
        currentDisplayId = display?.displayId ?: currentDisplayId
        lifecycleEvent = event
        Log.i(
            TAG,
            "TrackpadActivity $event displayId=$currentDisplayId " +
                "taskId=$taskId isTaskRoot=$isTaskRoot flags=${receivedIntentFlags.toHexFlags()}",
        )
    }

    private companion object {
        const val TAG = "AdventurePadLifecycle"
    }
}

@Composable
private fun TrackpadTouchTestScreen(
    display: Display?,
    diagnostics: ActivityRuntimeDiagnostics,
    onRestoreBothScreens: () -> Unit,
) {
    val touchState = remember { mutableStateOf(TouchState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackpadBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        CompactActivityHeader(
            display = display,
            diagnostics = diagnostics,
            onRestoreBothScreens = onRestoreBothScreens,
        )

        TouchSurface(
            touchState = touchState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        )

        TouchDiagnostics(touchState.value)
    }
}

@Composable
private fun CompactActivityHeader(
    display: Display?,
    diagnostics: ActivityRuntimeDiagnostics,
    onRestoreBothScreens: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TRACKPAD RELATIVE MOVEMENT TEST",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Display ID ${display?.displayId ?: diagnostics.displayId}  •  " +
                    "Task ID ${diagnostics.taskId}  •  Root ${diagnostics.isTaskRoot}  •  " +
                    "${diagnostics.lifecycleEvent}  •  Flags ${diagnostics.intentFlags.toHexFlags()}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onRestoreBothScreens,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = "RESTORE BOTH SCREENS",
                maxLines = 1,
            )
        }
    }
    Text(
        text = "Last result: ${diagnostics.lastResult}",
        color = Color(0xFFFFD166),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TouchSurface(
    touchState: MutableState<TouchState>,
    modifier: Modifier = Modifier,
) {
    val currentState by touchState
    val viewConfiguration = LocalViewConfiguration.current

    Box(
        modifier = modifier
            .background(TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder)
            .onSizeChanged { surfaceSize ->
                touchState.value = touchState.value.withSurfaceSize(
                    surfaceWidth = surfaceSize.width.toFloat(),
                    surfaceHeight = surfaceSize.height.toFloat(),
                )
            }
            .pointerInput(viewConfiguration.touchSlop) {
                val tapTracker = SingleFingerTapTracker(
                    maximumDurationMillis = ViewConfiguration.getTapTimeout().toLong(),
                    maximumDistance = viewConfiguration.touchSlop,
                )
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (tapTracker.handle(event)) {
                            ScummVMInputClient.sendTapLeftClick()
                        }
                    }
                }
            }
            // This simulated cursor remains app-local; this milestone performs no system injection.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val nextState = handlePointerEvent(
                            event = event,
                            previousState = touchState.value,
                            surfaceWidth = size.width.toFloat(),
                            surfaceHeight = size.height.toFloat(),
                        )
                        touchState.value = nextState
                        event.changes.forEach(PointerInputChange::consume)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = MarkerRadius.toPx()
            val unconstrainedCenter = if (currentState.cursorInitialized) {
                Offset(currentState.cursorX, currentState.cursorY)
            } else {
                center
            }
            val markerCenter = Offset(
                x = unconstrainedCenter.x.coerceIn(
                    radius,
                    (size.width - radius).coerceAtLeast(radius),
                ),
                y = unconstrainedCenter.y.coerceIn(
                    radius,
                    (size.height - radius).coerceAtLeast(radius),
                ),
            )
            drawCircle(
                Color.White,
                radius = radius + MarkerOutlineWidth.toPx(),
                center = markerCenter,
            )
            drawCircle(MarkerColor, radius = radius, center = markerCenter)
        }
        Text(
            text = "1:1 relative movement",
            color = TouchSurfaceBorder,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

private class SingleFingerTapTracker(
    private val maximumDurationMillis: Long,
    maximumDistance: Float,
) {
    private val maximumDistanceSquared = maximumDistance * maximumDistance
    private var trackedPointerId: PointerId? = null
    private var initialPosition = Offset.Zero
    private var downUptimeMillis = 0L
    private var eligible = false

    fun handle(event: PointerEvent): Boolean {
        val pressedCount = event.changes.count { it.pressed }
        when (event.type) {
            PointerEventType.Press -> {
                if (trackedPointerId == null && pressedCount == 1) {
                    val pointer = event.changes.first { it.pressed }
                    trackedPointerId = pointer.id
                    initialPosition = pointer.position
                    downUptimeMillis = pointer.uptimeMillis
                    eligible = true
                } else {
                    eligible = false
                }
            }

            PointerEventType.Move -> {
                val pointer = event.findPointer(trackedPointerId)
                if (pressedCount != 1 || pointer == null ||
                    (pointer.position - initialPosition).getDistanceSquared() > maximumDistanceSquared
                ) {
                    eligible = false
                }
            }

            PointerEventType.Release -> {
                val releasedPointer = event.changes.firstOrNull {
                    it.id == trackedPointerId && it.previousPressed && !it.pressed
                }
                if (releasedPointer != null && pressedCount == 0) {
                    val duration = releasedPointer.uptimeMillis - downUptimeMillis
                    val distanceSquared =
                        (releasedPointer.position - initialPosition).getDistanceSquared()
                    val recognised = eligible && duration in 0..maximumDurationMillis &&
                        distanceSquared <= maximumDistanceSquared
                    reset()
                    return recognised
                }
                if (pressedCount == 0) reset()
            }

            PointerEventType.Unknown -> if (pressedCount == 0) reset()
        }
        return false
    }

    private fun reset() {
        trackedPointerId = null
        eligible = false
        downUptimeMillis = 0L
    }
}

private fun KeyEvent.isFromGameController(): Boolean =
    isFromSource(InputDevice.SOURCE_GAMEPAD) || isFromSource(InputDevice.SOURCE_JOYSTICK)

private fun handlePointerEvent(
    event: PointerEvent,
    previousState: TouchState,
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val state = previousState.withSurfaceSize(surfaceWidth, surfaceHeight)
    val activePointerCount = event.changes.count { it.pressed }
    val action = when (event.type) {
        PointerEventType.Press -> if (state.pointerCount == 0) {
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
            state.trackedPointerId != null &&
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
            val initialPointer = event.changes.firstOrNull { it.pressed } ?: return state
            val position = initialPointer.position.constrainTo(surfaceWidth, surfaceHeight)

            // DOWN establishes only the finger baseline; it never relocates the persistent cursor.
            state.copy(
                fingerX = position.x,
                fingerY = position.y,
                deltaX = 0f,
                deltaY = 0f,
                pointerCount = activePointerCount,
                trackedPointerId = initialPointer.id,
                action = TouchAction.DOWN,
                moveEventCount = 0,
            )
        }

        TouchAction.MOVE -> {
            val trackedPointer = event.findPressedPointer(state.trackedPointerId)
            if (trackedPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                )
            } else if (trackedPointer.id != state.trackedPointerId) {
                state.withResetBaseline(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            } else {
                val updatedState = state.withRelativeMovement(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    moveEventCount = (state.moveEventCount + 1)
                        .coerceAtMost(MaxMoveEventCount),
                )
                CursorDeltaCoordinator.publish(
                    dx = updatedState.deltaX,
                    dy = updatedState.deltaY,
                )
                updatedState
            }
        }

        TouchAction.POINTER_DOWN -> {
            val trackedPointer = event.findPointer(state.trackedPointerId)
            if (trackedPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.POINTER_DOWN,
                )
            } else {
                // Additional fingers never replace the pointer selected at ACTION_DOWN.
                state.withResetBaseline(
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
            if (liftedPointerId == state.trackedPointerId) {
                val replacement = event.changes.firstOrNull { it.pressed }
                if (replacement == null) {
                    state.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        trackedPointerId = null,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    state.withResetBaseline(
                        pointer = replacement,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            } else {
                val trackedPointer = event.findPointer(state.trackedPointerId)
                if (trackedPointer == null) {
                    state.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    state.withResetBaseline(
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
            val finalPointer = event.findPointer(state.trackedPointerId)
            if (finalPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = 0,
                    trackedPointerId = null,
                    action = TouchAction.UP,
                )
            } else {
                val position = finalPointer.position.constrainTo(surfaceWidth, surfaceHeight)
                // UP clears the active baseline but leaves the accumulated cursor untouched.
                state.copy(
                    fingerX = position.x,
                    fingerY = position.y,
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = 0,
                    trackedPointerId = null,
                    action = TouchAction.UP,
                )
            }
        }

        TouchAction.CANCEL -> state.copy(
            deltaX = 0f,
            deltaY = 0f,
            pointerCount = 0,
            trackedPointerId = null,
            action = TouchAction.CANCEL,
            moveEventCount = 0,
        )

        null -> state.copy(pointerCount = activePointerCount)
    }
}

private fun TouchState.withRelativeMovement(
    pointer: PointerInputChange,
    pointerCount: Int,
    action: TouchAction,
    surfaceWidth: Float,
    surfaceHeight: Float,
    moveEventCount: Int = this.moveEventCount,
): TouchState {
    val position = pointer.position.constrainTo(surfaceWidth, surfaceHeight)
    val perEventDeltaX = position.x - fingerX
    val perEventDeltaY = position.y - fingerY
    return copy(
        fingerX = position.x,
        fingerY = position.y,
        deltaX = perEventDeltaX,
        deltaY = perEventDeltaY,
        // Finger coordinates are absolute; cursor coordinates accumulate only 1:1 MOVE deltas.
        cursorX = (cursorX + perEventDeltaX).coerceIn(0f, surfaceWidth.coerceAtLeast(0f)),
        cursorY = (cursorY + perEventDeltaY).coerceIn(0f, surfaceHeight.coerceAtLeast(0f)),
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
        moveEventCount = moveEventCount,
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
    // Resetting the baseline on new gestures and pointer handoff prevents cursor jumps.
    return copy(
        fingerX = position.x,
        fingerY = position.y,
        deltaX = 0f,
        deltaY = 0f,
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
    )
}

private fun TouchState.withSurfaceSize(
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val boundedWidth = surfaceWidth.coerceAtLeast(0f)
    val boundedHeight = surfaceHeight.coerceAtLeast(0f)
    return if (!cursorInitialized && boundedWidth > 0f && boundedHeight > 0f) {
        copy(
            cursorX = boundedWidth / 2f,
            cursorY = boundedHeight / 2f,
            cursorInitialized = true,
        )
    } else if (cursorInitialized) {
        copy(
            cursorX = cursorX.coerceIn(0f, boundedWidth),
            cursorY = cursorY.coerceIn(0f, boundedHeight),
        )
    } else {
        this
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        DiagnosticRow(
            "Finger X: ${touchState.fingerX.asCoordinate()}",
            "Finger Y: ${touchState.fingerY.asCoordinate()}",
            "Delta X: ${touchState.deltaX.asCoordinate()}",
            "Delta Y: ${touchState.deltaY.asCoordinate()}",
            "Action: ${touchState.action.label}",
        )
        DiagnosticRow(
            "Cursor X: ${touchState.cursorX.asCoordinate()}",
            "Cursor Y: ${touchState.cursorY.asCoordinate()}",
            "Pointers: ${touchState.pointerCount}",
            "Tracked ID: ${touchState.trackedPointerId?.value ?: "none"}",
            "Moves: ${touchState.moveEventCount}",
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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
        }
    }
}

private fun Float.asCoordinate(): String = String.format(Locale.US, "%.1f", this)

private data class TouchState(
    val fingerX: Float = 0f,
    val fingerY: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val cursorX: Float = 0f,
    val cursorY: Float = 0f,
    val cursorInitialized: Boolean = false,
    val pointerCount: Int = 0,
    val trackedPointerId: PointerId? = null,
    val action: TouchAction = TouchAction.CANCEL,
    val moveEventCount: Int = 0,
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
private const val AdventurePadBridgeTag = "AdventurePadBridge"
