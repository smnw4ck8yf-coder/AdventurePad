package com.jamesmoran.adventurepad

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.Display

internal enum class AndroidLaunchDestination {
    LAUNCHER,
    GAMEPLAY_TRACKPAD,
}

internal fun androidLaunchDestination(activeTargetId: String?): AndroidLaunchDestination =
    if (activeTargetId.isNullOrBlank()) {
        AndroidLaunchDestination.LAUNCHER
    } else {
        AndroidLaunchDestination.GAMEPLAY_TRACKPAD
    }

/** Routes the app icon according to the authoritative active target published by ScummVM. */
class LauncherRoutingActivity : Activity() {
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var bridgeBound = false
    private var routed = false
    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != MirrorSurfaceProtocol.MSG_GEOMETRY) return
            routeFromActiveTarget(message.data.getString(MirrorSurfaceProtocol.KEY_GAME_ID))
        }
    })
    private val bridgeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val query = Message.obtain(null, MirrorSurfaceProtocol.MSG_QUERY_GEOMETRY).apply {
                replyTo = replyMessenger
            }
            try {
                Messenger(service).send(query)
            } catch (exception: RemoteException) {
                Log.w(TAG, "Could not query the active ScummVM target", exception)
                routeFromActiveTarget(null)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = routeFromActiveTarget(null)
        override fun onBindingDied(name: ComponentName) = routeFromActiveTarget(null)
        override fun onNullBinding(name: ComponentName) = routeFromActiveTarget(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timeoutHandler.postDelayed({ routeFromActiveTarget(null) }, BRIDGE_QUERY_TIMEOUT_MILLIS)
        val bridgeIntent = Intent().setComponent(ComponentName(SCUMMVM_PACKAGE, SCUMMVM_SERVICE))
        bridgeBound = try {
            bindService(bridgeIntent, bridgeConnection, Context.BIND_AUTO_CREATE)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not bind to the ScummVM bridge", exception)
            false
        }
        if (!bridgeBound) routeFromActiveTarget(null)
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null)
        disconnectBridge()
        super.onDestroy()
    }

    private fun routeFromActiveTarget(activeTargetId: String?) {
        if (routed) return
        routed = true
        timeoutHandler.removeCallbacksAndMessages(null)
        disconnectBridge()

        when (androidLaunchDestination(activeTargetId)) {
            AndroidLaunchDestination.GAMEPLAY_TRACKPAD -> {
                val result = DualDisplayCoordinator.restoreGameplayTrackpad(
                    activity = this,
                    reason = "Android app icon opened during active game '$activeTargetId'",
                )
                Log.i(TAG, result.message)
            }
            AndroidLaunchDestination.LAUNCHER -> launchMainActivity()
        }
        finish()
    }

    private fun launchMainActivity() {
        val launcherIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON, "AdventurePad launcher request")
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()
        startActivity(launcherIntent, options)
    }

    private fun disconnectBridge() {
        if (!bridgeBound) return
        try {
            unbindService(bridgeConnection)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not unbind the ScummVM bridge probe", exception)
        }
        bridgeBound = false
    }

    private companion object {
        const val TAG = "AdventurePadLaunch"
        const val SCUMMVM_PACKAGE = "org.scummvm.scummvm.debug"
        const val SCUMMVM_SERVICE = "org.scummvm.scummvm.RelativeInputService"
        const val BRIDGE_QUERY_TIMEOUT_MILLIS = 3_000L
    }
}
