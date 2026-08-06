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
    private const val RECONNECT_DELAY_MILLIS = 2_000L
    private const val CONNECTION_TIMEOUT_MILLIS = 5_000L

    private var applicationContext: Context? = null
    private val bindingTracker = BindingRequestTracker()
    private var remoteMessenger: Messenger? = null
    private var lastConnectionEvent = "IDLE"
    private var connectionStateListener: ((ScummVMConnectionDiagnostics) -> Unit)? = null
    private var mirrorStatusListener: ((MirrorOutputStatus) -> Unit)? = null
    private var mirrorStatus = MirrorOutputStatus()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != MirrorSurfaceProtocol.MSG_STATUS) {
                Log.w(TAG, "Ignored unknown ScummVM reply type ${message.what}")
                return
            }
            val data = message.data
            val generation = data.getLong(MirrorSurfaceProtocol.KEY_GENERATION, 0)
            if (generation < mirrorStatus.generation) {
                Log.i(TAG, "Ignored stale mirror status for generation $generation")
                return
            }
            mirrorStatus = MirrorOutputStatus(
                state = MirrorOutputState.fromWireValue(
                    data.getInt(MirrorSurfaceProtocol.KEY_STATUS, MirrorSurfaceProtocol.STATUS_FAILED),
                ),
                generation = generation,
                diagnostic = data.getString(MirrorSurfaceProtocol.KEY_DIAGNOSTIC).orEmpty(),
            )
            mirrorStatusListener?.invoke(mirrorStatus)
        }
    })
    private val lastJoystickPositions = mutableMapOf<JoystickAxisKey, Int>()
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
        return sendMirrorMessage(MirrorSurfaceProtocol.MSG_ATTACH_SURFACE, data)
    }

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

    fun sendJoystickMotion(event: MotionEvent): Boolean {
        val messenger = remoteMessenger ?: return false
        val device = event.device ?: return false
        val mappings = joystickMappingsFor(device)
        if (mappings.isEmpty()) return false

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
            if (lastJoystickPositions[key] == position) continue

            val message = Message.obtain(null, MSG_JOYSTICK_AXIS).apply {
                arg1 = mapping.scummVMBitFlag
                arg2 = position
            }
            try {
                messenger.send(message)
                lastJoystickPositions[key] = position
                if (loggedJoystickAxes.add(key)) {
                    Log.i(
                        BRIDGE_TAG,
                        "Axis mapped: deviceId=${event.deviceId} " +
                            "${MotionEvent.axisToString(mapping.androidAxis)}=${event.getAxisValue(mapping.androidAxis)} " +
                            "-> JE_JOYSTICK flag=0x${mapping.scummVMBitFlag.toString(16)}",
                    )
                }
            } catch (exception: RemoteException) {
                handleConnectionLoss("JOYSTICK SEND FAILED", replaceBinding = true)
                Log.w(TAG, "Joystick forwarding failed", exception)
                return false
            }
        }
        return true
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
