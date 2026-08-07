package com.jamesmoran.adventurepad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ScummVMConnectionDiagnostics(
    val isConnected: Boolean = false,
    val bindingRequested: Boolean = false,
    val reconnectAttemptCount: Int = 0,
    val lastConnectionEvent: String = "IDLE",
)

/** Explicit, lifecycle-bound Messenger connection to the custom ScummVM debug app. */
internal object ScummVMInputClient {
    private const val TAG = "AdventurePadIPC"
    private const val SCUMMVM_PACKAGE = "org.scummvm.scummvm.debug"
    private const val SCUMMVM_SERVICE = "org.scummvm.scummvm.RelativeInputService"
    private const val MSG_RELATIVE_MOVE = 1
    private const val MSG_JOYSTICK_AXIS = 6
    private const val MSG_GAMEPAD_KEY = 7
    private const val JOYSTICK_AXIS_MAX = 32767
    private const val ANDROID_JOYSTICK_DEAD_ZONE = 0.209f
    private const val JOYSTICK_HAT_SCALE = 0.66f
    private const val BRIDGE_TAG = "AdventurePadBridge"
    private const val INITIALIZATION_TAG = "AdventurePadMirrorInit"
    private const val MAX_INITIALIZATION_LOGS = 64
    private const val RECONNECT_DELAY_MILLIS = 2_000L
    private const val CONNECTION_TIMEOUT_MILLIS = 5_000L

    private var applicationContext: Context? = null
    private val bindingTracker = BindingRequestTracker()
    private var remoteMessenger: Messenger? = null
    private var lastConnectionEvent = "IDLE"
    private var connectionStateListener: ((ScummVMConnectionDiagnostics) -> Unit)? = null
    private var mirrorStatusListener: ((MirrorOutputStatus) -> Unit)? = null
    private var mirrorGeometryListener: ((MirrorSourceGeometry) -> Unit)? = null
    private var cropAcknowledgementListener: ((CropAcknowledgement) -> Unit)? = null
    private var upperPresentationAcknowledgementListener: ((UpperPresentationAcknowledgement) -> Unit)? = null
    private var mirrorCursorListener: ((MirrorCursorState) -> Unit)? = null
    private var mirrorStatus = MirrorOutputStatus()
    private var initializationLogCount = 0
    private var firstCursorReplyLogged = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data
            if (message.what == MirrorSurfaceProtocol.MSG_GEOMETRY) {
                logInitialization(
                    "reply GEOMETRY generation=${data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0)} " +
                        "source=${data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_WIDTH, 0)}x" +
                        data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_HEIGHT, 0),
                )
                mirrorGeometryListener?.invoke(
                    MirrorSourceGeometry(
                        width = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_WIDTH, 0),
                        height = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_HEIGHT, 0),
                        rendererCapability = data.getInt(MirrorSurfaceProtocol.KEY_RENDERER_CAPABILITY, 0),
                        generation = data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0),
                        gameId = data.getString(MirrorSurfaceProtocol.KEY_GAME_ID).orEmpty(),
                        orientation = SourceOrientation.fromWireValue(
                            data.getInt(MirrorSurfaceProtocol.KEY_ORIENTATION, 0),
                        ),
                    ),
                )
                return
            }
            if (message.what == MirrorSurfaceProtocol.MSG_CROP_ACK) {
                logInitialization(
                    "reply CROP_ACK cropGeneration=" +
                        data.getLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, 0) +
                        " geometryGeneration=" +
                        data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0) +
                        " result=${data.getInt(MirrorSurfaceProtocol.KEY_CROP_RESULT, 0)}",
                )
                cropAcknowledgementListener?.invoke(
                    CropAcknowledgement(
                        result = CropAcknowledgementResult.fromWireValue(
                            data.getInt(MirrorSurfaceProtocol.KEY_CROP_RESULT, 0),
                        ),
                        cropGeneration = data.getLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, 0),
                        geometryGeneration = data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0),
                        diagnostic = data.getString(MirrorSurfaceProtocol.KEY_DIAGNOSTIC).orEmpty(),
                    ),
                )
                return
            }
            if (message.what == MirrorSurfaceProtocol.MSG_DISPLAY_MODE_ACK) {
                logInitialization(
                    "reply MODE_ACK modeGeneration=" +
                        data.getLong(MirrorSurfaceProtocol.KEY_MODE_GENERATION, 0) +
                        " geometryGeneration=" +
                        data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0) +
                        " result=${data.getInt(MirrorSurfaceProtocol.KEY_MODE_RESULT, 0)}",
                )
                upperPresentationAcknowledgementListener?.invoke(
                    UpperPresentationAcknowledgement(
                        result = UpperPresentationResult.fromWireValue(
                            data.getInt(MirrorSurfaceProtocol.KEY_MODE_RESULT, 0),
                        ),
                        modeGeneration = data.getLong(MirrorSurfaceProtocol.KEY_MODE_GENERATION, 0),
                        geometryGeneration = data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0),
                        diagnostic = data.getString(MirrorSurfaceProtocol.KEY_DIAGNOSTIC).orEmpty(),
                    ),
                )
                return
            }
            if (message.what == MirrorSurfaceProtocol.MSG_CURSOR_POSITION) {
                if (!firstCursorReplyLogged) {
                    firstCursorReplyLogged = true
                    logInitialization(
                        "reply FIRST_CURSOR geometryGeneration=" +
                            data.getLong(MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION, 0) +
                            " point=${data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_X, 0)}," +
                            data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_Y, 0),
                    )
                }
                mirrorCursorListener?.invoke(
                    MirrorCursorState(
                        point = SourcePoint(
                            x = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_X, 0),
                            y = data.getInt(MirrorSurfaceProtocol.KEY_SOURCE_Y, 0),
                        ),
                        visible = data.getBoolean(MirrorSurfaceProtocol.KEY_CURSOR_VISIBLE, false),
                        geometryGeneration = data.getLong(
                            MirrorSurfaceProtocol.KEY_GEOMETRY_GENERATION,
                            0,
                        ),
                    ),
                )
                return
            }
            if (message.what != MirrorSurfaceProtocol.MSG_STATUS) {
                Log.w(TAG, "Ignored unknown ScummVM reply type ${message.what}")
                return
            }
            val generation = data.getLong(MirrorSurfaceProtocol.KEY_GENERATION, 0)
            val state = MirrorOutputState.fromWireValue(
                data.getInt(MirrorSurfaceProtocol.KEY_STATUS, MirrorSurfaceProtocol.STATUS_FAILED),
            )
            logInitialization(
                "reply STATUS state=${state.name} generation=$generation " +
                    "previous=${mirrorStatus.state.name}/${mirrorStatus.generation}",
            )
            if (generation < mirrorStatus.generation) {
                Log.i(TAG, "Ignored stale mirror status for generation $generation")
                return
            }
            mirrorStatus = MirrorOutputStatus(
                state = state,
                generation = generation,
                diagnostic = data.getString(MirrorSurfaceProtocol.KEY_DIAGNOSTIC).orEmpty(),
            )
            mirrorStatusListener?.invoke(mirrorStatus)
        }
    })
    private val lastJoystickPositions = mutableMapOf<JoystickAxisKey, Int>()
    private val lastObservedTriggerPositions = mutableMapOf<JoystickAxisKey, Int>()
    private val loggedGamepadDevices = mutableSetOf<Int>()
    private val loggedJoystickAxes = mutableSetOf<JoystickAxisKey>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!bindingTracker.bindingDesired || !bindingTracker.bindingRequested) {
                Log.w(TAG, "Ignoring stale Messenger connection callback for $name")
                return
            }
            remoteMessenger = Messenger(service)
            lastConnectionEvent = "CONNECTED"
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.removeCallbacks(connectionTimeoutRunnable)
            notifyConnectionState()
            logInitialization("Messenger connected component=$name")
            Log.i(TAG, "Messenger connected to $name")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            handleConnectionLoss("SERVICE DISCONNECTED", replaceBinding = false)
            Log.w(TAG, "Messenger disconnected from $name")
        }

        override fun onBindingDied(name: ComponentName) {
            handleConnectionLoss("BINDING DIED", replaceBinding = true)
            Log.e(TAG, "Messenger binding died for $name")
        }

        override fun onNullBinding(name: ComponentName) {
            handleConnectionLoss("NULL BINDING", replaceBinding = true)
            Log.e(TAG, "ScummVM returned a null Messenger binding for $name")
        }
    }
    private val reconnectRunnable = Runnable {
        if (remoteMessenger != null || !bindingTracker.beginReconnectAttempt()) return@Runnable
        lastConnectionEvent = "RECONNECT ATTEMPT ${bindingTracker.reconnectAttemptCount}"
        notifyConnectionState()
        requestBinding()
    }
    private val connectionTimeoutRunnable = Runnable {
        if (!bindingTracker.bindingDesired ||
            !bindingTracker.bindingRequested ||
            remoteMessenger != null
        ) {
            return@Runnable
        }
        lastConnectionEvent = "CONNECTION TIMEOUT"
        abandonCurrentBinding()
        notifyConnectionState()
        scheduleReconnect()
    }

    @Synchronized
    fun bind(context: Context) {
        if (!bindingTracker.start()) return
        initializationLogCount = 0
        firstCursorReplyLogged = false
        logInitialization("bind requested")
        applicationContext = context.applicationContext
        lastConnectionEvent = "BIND REQUESTED"
        notifyConnectionState()
        requestBinding()
    }

    @Synchronized
    private fun requestBinding() {
        if (!bindingTracker.canRequestBinding()) return
        val appContext = applicationContext ?: return
        val intent = Intent().setComponent(ComponentName(SCUMMVM_PACKAGE, SCUMMVM_SERVICE))
        val accepted = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound ->
                if (bound) {
                    lastConnectionEvent = "WAITING FOR SERVICE"
                    Log.i(TAG, "Messenger bind requested for ${intent.component}")
                } else {
                    lastConnectionEvent = "BIND REJECTED"
                    Log.w(TAG, "Messenger bind was rejected for ${intent.component}")
                }
            }
        } catch (exception: RuntimeException) {
            lastConnectionEvent = "BIND FAILED: ${exception.javaClass.simpleName}"
            Log.w(TAG, "Messenger bind failed; standalone mode remains available", exception)
            false
        }
        bindingTracker.recordRequestResult(accepted)
        notifyConnectionState()
        if (bindingTracker.bindingRequested) {
            scheduleConnectionTimeout()
        } else {
            scheduleReconnect()
        }
    }

    @Synchronized
    fun setConnectionStateListener(listener: ((ScummVMConnectionDiagnostics) -> Unit)?) {
        connectionStateListener = listener
        listener?.invoke(connectionDiagnostics())
    }

    @Synchronized
    fun setMirrorStatusListener(listener: ((MirrorOutputStatus) -> Unit)?) {
        mirrorStatusListener = listener
        listener?.invoke(mirrorStatus)
    }

    @Synchronized
    fun setMirrorGeometryListener(listener: ((MirrorSourceGeometry) -> Unit)?) {
        mirrorGeometryListener = listener
    }

    @Synchronized
    fun setCropAcknowledgementListener(listener: ((CropAcknowledgement) -> Unit)?) {
        cropAcknowledgementListener = listener
    }

    @Synchronized
    fun setUpperPresentationAcknowledgementListener(
        listener: ((UpperPresentationAcknowledgement) -> Unit)?,
    ) {
        upperPresentationAcknowledgementListener = listener
    }

    @Synchronized
    fun setMirrorCursorListener(listener: ((MirrorCursorState) -> Unit)?) {
        mirrorCursorListener = listener
    }

    fun queryMirrorGeometry(): Boolean = sendMirrorMessage(
        MirrorSurfaceProtocol.MSG_QUERY_GEOMETRY,
        Bundle.EMPTY,
    ).also { sent -> logInitialization("send QUERY_GEOMETRY sent=$sent") }

    fun applyMirrorCrop(
        crop: NormalizedCrop,
        cropGeneration: Long,
        expectedGeometryGeneration: Long,
    ): Boolean {
        if (!crop.isValid() || cropGeneration <= 0 || expectedGeometryGeneration <= 0) return false
        val data = Bundle().apply {
            putFloat(MirrorSurfaceProtocol.KEY_LEFT, crop.left)
            putFloat(MirrorSurfaceProtocol.KEY_TOP, crop.top)
            putFloat(MirrorSurfaceProtocol.KEY_RIGHT, crop.right)
            putFloat(MirrorSurfaceProtocol.KEY_BOTTOM, crop.bottom)
            putLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, cropGeneration)
            putLong(MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, expectedGeometryGeneration)
        }
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_APPLY_CROP, data).also { sent ->
            logInitialization(
                "send CROP generation=$cropGeneration geometryGeneration=$expectedGeometryGeneration sent=$sent",
            )
        }
    }

    fun applyDisplayMode(
        mode: DisplayMode,
        crop: NormalizedCrop,
        modeGeneration: Long,
        expectedGeometryGeneration: Long,
    ): Boolean {
        if (!crop.isValid() || modeGeneration <= 0 || expectedGeometryGeneration <= 0) return false
        val data = Bundle().apply {
            putInt(MirrorSurfaceProtocol.KEY_DISPLAY_MODE, if (mode == DisplayMode.INTERFACE) 1 else 0)
            putLong(MirrorSurfaceProtocol.KEY_MODE_GENERATION, modeGeneration)
            putLong(MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, expectedGeometryGeneration)
            putFloat(MirrorSurfaceProtocol.KEY_LEFT, crop.left)
            putFloat(MirrorSurfaceProtocol.KEY_TOP, crop.top)
            putFloat(MirrorSurfaceProtocol.KEY_RIGHT, crop.right)
            putFloat(MirrorSurfaceProtocol.KEY_BOTTOM, crop.bottom)
        }
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_APPLY_DISPLAY_MODE, data).also { sent ->
            logInitialization(
                "send MODE mode=${mode.name} generation=$modeGeneration " +
                    "geometryGeneration=$expectedGeometryGeneration sent=$sent",
            )
        }
    }

    fun sendAbsoluteSourcePointer(
        command: AbsoluteSourcePointerCommand,
        pointerId: Int,
    ): Boolean {
        if (command.point.x < 0 || command.point.y < 0 || command.cropGeneration <= 0 ||
            command.geometryGeneration <= 0 || command.pointerSequenceId <= 0 || pointerId < 0
        ) return false
        val data = Bundle().apply {
            putInt(MirrorSurfaceProtocol.KEY_SOURCE_X, command.point.x)
            putInt(MirrorSurfaceProtocol.KEY_SOURCE_Y, command.point.y)
            putInt(MirrorSurfaceProtocol.KEY_POINTER_ACTION, command.action.wireValue)
            putInt(MirrorSurfaceProtocol.KEY_POINTER_ID, pointerId)
            putLong(MirrorSurfaceProtocol.KEY_POINTER_SEQUENCE_ID, command.pointerSequenceId)
            putLong(MirrorSurfaceProtocol.KEY_CROP_GENERATION, command.cropGeneration)
            putLong(MirrorSurfaceProtocol.KEY_EXPECTED_GEOMETRY_GENERATION, command.geometryGeneration)
        }
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_ABSOLUTE_SOURCE_POINTER, data)
    }

    @Synchronized
    fun unbind() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(connectionTimeoutRunnable)
        val context = applicationContext
        if (bindingTracker.bindingRequested && context != null) {
            try {
                context.unbindService(connection)
                Log.i(TAG, "Messenger unbound")
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Messenger unbind failed", exception)
            }
        }
        remoteMessenger = null
        clearGamepadState()
        bindingTracker.stop()
        applicationContext = null
        lastConnectionEvent = "UNBOUND"
        mirrorStatus = MirrorOutputStatus(
            state = MirrorOutputState.DETACHED,
            generation = mirrorStatus.generation,
            diagnostic = "Messenger disconnected.",
        )
        notifyConnectionState()
        mirrorStatusListener?.invoke(mirrorStatus)
    }

    fun attachMirrorSurface(
        surface: Surface,
        generation: Long,
        width: Int,
        height: Int,
        displayId: Int,
    ): Boolean {
        if (!surface.isValid || generation <= 0 || width <= 0 || height <= 0) return false
        val data = Bundle().apply {
            putParcelable(MirrorSurfaceProtocol.KEY_SURFACE, surface)
            putLong(MirrorSurfaceProtocol.KEY_GENERATION, generation)
            putInt(MirrorSurfaceProtocol.KEY_WIDTH, width)
            putInt(MirrorSurfaceProtocol.KEY_HEIGHT, height)
            putInt(MirrorSurfaceProtocol.KEY_DISPLAY_ID, displayId)
        }
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_ATTACH_SURFACE, data).also { sent ->
            logInitialization(
                "send ATTACH generation=$generation size=${width}x$height display=$displayId sent=$sent",
            )
        }
    }

    @Synchronized
    fun isConnected(): Boolean = remoteMessenger != null

    fun detachMirrorSurface(generation: Long): Boolean {
        if (generation <= 0) return false
        val data = Bundle().apply {
            putLong(MirrorSurfaceProtocol.KEY_GENERATION, generation)
        }
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_DETACH_SURFACE, data)
    }

    private fun sendMirrorMessage(messageType: Int, data: Bundle): Boolean {
        val messenger = remoteMessenger ?: return false
        val message = Message.obtain(null, messageType).apply {
            this.data = data
            replyTo = replyMessenger
        }
        return try {
            messenger.send(message)
            true
        } catch (exception: RemoteException) {
            handleConnectionLoss("MIRROR SEND FAILED", replaceBinding = true)
            Log.w(TAG, "Mirror surface message failed; reconnect scheduled", exception)
            false
        }
    }

    private fun logInitialization(message: String) {
        if (initializationLogCount >= MAX_INITIALIZATION_LOGS) return
        initializationLogCount += 1
        Log.i(INITIALIZATION_TAG, "event=$initializationLogCount/$MAX_INITIALIZATION_LOGS $message")
    }

    fun sendRelativeDelta(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) {
            Log.w(TAG, "Rejected non-finite relative delta dx=$dx dy=$dy")
            return
        }

        val messenger = remoteMessenger ?: return
        val message = Message.obtain(null, MSG_RELATIVE_MOVE).apply {
            arg1 = java.lang.Float.floatToIntBits(dx)
            arg2 = java.lang.Float.floatToIntBits(dy)
        }
        try {
            messenger.send(message)
        } catch (exception: RemoteException) {
            handleConnectionLoss("MOVE SEND FAILED", replaceBinding = true)
            Log.w(TAG, "Messenger send failed; reconnect scheduled", exception)
        }
    }

    fun sendButtonEvent(event: ScummVMButtonEvent): Boolean {
        val messenger = remoteMessenger ?: return false
        val message = Message.obtain(null, event.messageWhat)
        return try {
            messenger.send(message)
            Log.i(BRIDGE_TAG, "Button event forwarded: ${event.name}")
            true
        } catch (exception: RemoteException) {
            handleConnectionLoss("BUTTON SEND FAILED", replaceBinding = true)
            Log.w(TAG, "Button event forwarding failed", exception)
            false
        }
    }

    fun sendJoystickMotion(
        event: MotionEvent,
        onTriggerAxis: ((TriggerAxisValue) -> Unit)? = null,
    ): Boolean {
        val device = event.device ?: return false
        val mappings = joystickMappingsFor(device)
        if (mappings.isEmpty()) return false
        var handled = false

        if (loggedGamepadDevices.add(event.deviceId)) {
            Log.i(
                BRIDGE_TAG,
                "Gamepad motion accepted: deviceId=${event.deviceId} name=${device.name} " +
                    "source=0x${event.source.toString(16)}",
            )
        }

        for (mapping in mappings) {
            val key = JoystickAxisKey(event.deviceId, mapping.scummVMBitFlag)
            val value = centeredAxisValue(event, device, mapping)
            val position = (value * JOYSTICK_AXIS_MAX).roundToInt()

            if (mapping.scummVMBitFlag == LEFT_TRIGGER_AXIS_FLAG ||
                mapping.scummVMBitFlag == RIGHT_TRIGGER_AXIS_FLAG
            ) {
                if (onTriggerAxis != null) {
                    // Chord recognition belongs to AdventurePad and must not wait for the
                    // asynchronous ScummVM Messenger connection.
                    handled = true
                    if (lastObservedTriggerPositions[key] == position) continue
                    lastObservedTriggerPositions[key] = position
                    logInitialization(
                        "observe TRIGGER axis=0x${mapping.scummVMBitFlag.toString(16)} " +
                            "position=$position bridgeConnected=${remoteMessenger != null}",
                    )
                    onTriggerAxis(TriggerAxisValue(event.deviceId, mapping.scummVMBitFlag, position))
                    continue
                }
            }
            if (lastJoystickPositions[key] == position) continue

            if (sendJoystickAxisValue(TriggerAxisValue(event.deviceId, mapping.scummVMBitFlag, position))) {
                handled = true
                if (loggedJoystickAxes.add(key)) {
                    Log.i(
                        BRIDGE_TAG,
                        "Axis mapped: deviceId=${event.deviceId} " +
                            "${MotionEvent.axisToString(mapping.androidAxis)}=${event.getAxisValue(mapping.androidAxis)} " +
                            "-> JE_JOYSTICK flag=0x${mapping.scummVMBitFlag.toString(16)}",
                    )
                }
            }
        }
        return handled
    }

    fun sendJoystickAxisValue(value: TriggerAxisValue): Boolean {
        if (value.axisFlag == 0 || value.position !in -JOYSTICK_AXIS_MAX..JOYSTICK_AXIS_MAX) return false
        val messenger = remoteMessenger ?: return false
        val key = JoystickAxisKey(value.deviceId, value.axisFlag)
        if (lastJoystickPositions[key] == value.position) return true
        val message = Message.obtain(null, MSG_JOYSTICK_AXIS).apply {
            arg1 = value.axisFlag
            arg2 = value.position
        }
        return try {
            messenger.send(message)
            lastJoystickPositions[key] = value.position
            true
        } catch (exception: RemoteException) {
            handleConnectionLoss("JOYSTICK SEND FAILED", replaceBinding = true)
            Log.w(TAG, "Joystick forwarding failed", exception)
            false
        }
    }

    fun releaseJoystickAxes() {
        val messenger = remoteMessenger
        if (messenger != null) {
            lastJoystickPositions.filterValues { it != 0 }.forEach { (key, _) ->
                val message = Message.obtain(null, MSG_JOYSTICK_AXIS).apply {
                    arg1 = key.scummVMBitFlag
                    arg2 = 0
                }
                try {
                    messenger.send(message)
                } catch (exception: RemoteException) {
                    handleConnectionLoss("JOYSTICK RELEASE FAILED", replaceBinding = true)
                    Log.w(TAG, "Joystick release forwarding failed", exception)
                    return@forEach
                }
            }
        }
        lastJoystickPositions.clear()
        lastObservedTriggerPositions.clear()
    }

    fun sendGamepadKeyEvent(action: Int, keyCode: Int): Boolean {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        if (!isForwardedGamepadKey(keyCode)) return false

        val messenger = remoteMessenger ?: return false
        val message = Message.obtain(null, MSG_GAMEPAD_KEY).apply {
            arg1 = action
            arg2 = keyCode
        }
        return try {
            messenger.send(message)
            Log.i(
                BRIDGE_TAG,
                "Gamepad key forwarded: ${KeyEvent.keyCodeToString(keyCode)} " +
                    if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP",
            )
            true
        } catch (exception: RemoteException) {
            handleConnectionLoss("KEY SEND FAILED", replaceBinding = true)
            Log.w(TAG, "Gamepad key forwarding failed", exception)
            false
        }
    }

    fun isForwardedGamepadKey(keyCode: Int): Boolean = keyCode in ForwardedGamepadKeyCodes

    @Synchronized
    private fun handleConnectionLoss(event: String, replaceBinding: Boolean) {
        val wasConnected = remoteMessenger != null
        remoteMessenger = null
        clearGamepadState()
        lastConnectionEvent = event
        if (replaceBinding) abandonCurrentBinding()
        if (wasConnected || replaceBinding) notifyConnectionState()
        if (replaceBinding) scheduleReconnect() else scheduleConnectionTimeout()
    }

    private fun abandonCurrentBinding() {
        mainHandler.removeCallbacks(connectionTimeoutRunnable)
        val context = applicationContext
        if (bindingTracker.bindingRequested && context != null) {
            try {
                context.unbindService(connection)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Failed to discard unusable Messenger binding", exception)
            }
        }
        bindingTracker.discardBinding()
    }

    private fun scheduleConnectionTimeout() {
        if (!bindingTracker.bindingDesired ||
            !bindingTracker.bindingRequested ||
            remoteMessenger != null
        ) {
            return
        }
        mainHandler.removeCallbacks(connectionTimeoutRunnable)
        mainHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MILLIS)
    }

    private fun scheduleReconnect() {
        if (!bindingTracker.bindingDesired || remoteMessenger != null) return
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MILLIS)
    }

    private fun connectionDiagnostics() = ScummVMConnectionDiagnostics(
        isConnected = remoteMessenger != null,
        bindingRequested = bindingTracker.bindingRequested,
        reconnectAttemptCount = bindingTracker.reconnectAttemptCount,
        lastConnectionEvent = lastConnectionEvent,
    )

    private fun notifyConnectionState() {
        connectionStateListener?.invoke(connectionDiagnostics())
    }

    private fun joystickMappingsFor(device: InputDevice): List<JoystickAxisMapping> {
        val mappings = listOfNotNull(
            mappingIfPresent(device, MotionEvent.AXIS_X, 0x01),
            mappingIfPresent(device, MotionEvent.AXIS_Y, 0x02),
            mappingIfPresent(device, MotionEvent.AXIS_HAT_X, 0x04, JOYSTICK_HAT_SCALE),
            mappingIfPresent(device, MotionEvent.AXIS_HAT_Y, 0x08, JOYSTICK_HAT_SCALE),
            mappingIfPresent(device, MotionEvent.AXIS_Z, 0x10),
            mappingIfPresent(device, MotionEvent.AXIS_RZ, 0x20),
        ).toMutableList()

        // Android gamepads may expose triggers either as BRAKE/GAS or LTRIGGER/RTRIGGER.
        (mappingIfPresent(device, MotionEvent.AXIS_BRAKE, 0x40)
            ?: mappingIfPresent(device, MotionEvent.AXIS_LTRIGGER, 0x40))
            ?.let(mappings::add)
        (mappingIfPresent(device, MotionEvent.AXIS_GAS, 0x80)
            ?: mappingIfPresent(device, MotionEvent.AXIS_RTRIGGER, 0x80))
            ?.let(mappings::add)
        return mappings
    }

    private fun mappingIfPresent(
        device: InputDevice,
        androidAxis: Int,
        scummVMBitFlag: Int,
        scale: Float = 1f,
    ): JoystickAxisMapping? = device.getMotionRange(androidAxis, InputDevice.SOURCE_JOYSTICK)
        ?.let { JoystickAxisMapping(androidAxis, scummVMBitFlag, scale) }

    private fun centeredAxisValue(
        event: MotionEvent,
        device: InputDevice,
        mapping: JoystickAxisMapping,
    ): Float {
        val range = device.getMotionRange(mapping.androidAxis, InputDevice.SOURCE_JOYSTICK)
        val rawValue = event.getAxisValue(mapping.androidAxis)
        val deadZone = maxOf(range?.flat ?: 0f, ANDROID_JOYSTICK_DEAD_ZONE)
        val centeredValue = if (abs(rawValue) < deadZone) 0f else rawValue
        return (centeredValue * mapping.scale).coerceIn(-1f, 1f)
    }

    private fun clearGamepadState() {
        lastJoystickPositions.clear()
        lastObservedTriggerPositions.clear()
    }
}

private data class JoystickAxisKey(
    val deviceId: Int,
    val scummVMBitFlag: Int,
)

private data class JoystickAxisMapping(
    val androidAxis: Int,
    val scummVMBitFlag: Int,
    val scale: Float,
)

private val ForwardedGamepadKeyCodes = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_L2,
    KeyEvent.KEYCODE_BUTTON_R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    KeyEvent.KEYCODE_BUTTON_MODE,
)

internal enum class ScummVMButtonEvent(internal val messageWhat: Int) {
    LEFT_BUTTON_DOWN(2),
    LEFT_BUTTON_UP(3),
    RIGHT_BUTTON_DOWN(4),
    RIGHT_BUTTON_UP(5),
}

internal enum class ScummVMMouseButton(
    internal val downEvent: ScummVMButtonEvent,
    internal val upEvent: ScummVMButtonEvent,
) {
    LEFT(ScummVMButtonEvent.LEFT_BUTTON_DOWN, ScummVMButtonEvent.LEFT_BUTTON_UP),
    RIGHT(ScummVMButtonEvent.RIGHT_BUTTON_DOWN, ScummVMButtonEvent.RIGHT_BUTTON_UP),
}
