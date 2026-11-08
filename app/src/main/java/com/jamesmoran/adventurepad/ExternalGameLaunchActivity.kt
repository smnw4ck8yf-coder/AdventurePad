package com.jamesmoran.adventurepad

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Public, target-only entry point for gaming frontends such as Cocoon. */
@SuppressLint("CustomSplashScreen") // This is a translucent intent router, not a launch screen.
class ExternalGameLaunchActivity : ComponentActivity() {
    private val requestCoordinator = ExternalGameLaunchRequestCoordinator()
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private lateinit var libraryClient: ScummVMLibraryClient
    private lateinit var launcherMetadataRepository: LauncherLibraryMetadataRepository
    private var libraryState = ScummVMLibraryState()
    private var terminal = false
    private var retryNotBeforeUptime = 0L
    private var nextDispatchNotBeforeUptime = 0L
    private var retryArmed = false

    private val retryRunnable = Runnable { resolvePendingRequest() }

    private val timeoutRunnable = Runnable {
        requestCoordinator.current()?.let { request ->
            finishWithError("AdventurePad could not confirm configured target '${request.targetId}' in time.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherMetadataRepository = LauncherLibraryMetadataRepository.create(this, lifecycleScope)
        libraryClient = ScummVMLibraryClient(
            context = this,
            onStateChanged = { state ->
                val lostConnection = libraryState.connected && !state.connected
                libraryState = state
                if (lostConnection && requestCoordinator.current()?.attempts?.let { it > 0 } == true) {
                    retryArmed = true
                    retryNotBeforeUptime = SystemClock.uptimeMillis() + PROCESS_RESTART_SETTLE_MILLIS
                    timeoutHandler.removeCallbacks(retryRunnable)
                    timeoutHandler.postAtTime(retryRunnable, retryNotBeforeUptime)
                }
                resolvePendingRequest()
            },
            // A process-restart retry must not race ScummVM's hidden save-capability refresh.
            refreshSaveCapabilitiesOnConnect = false,
        )
        acceptIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!terminal) libraryClient.connect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
        if (!libraryState.connected) libraryClient.connect()
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null)
        libraryClient.disconnect()
        super.onDestroy()
    }

    private fun acceptIntent(intent: Intent) {
        terminal = false
        when (val result = parseExternalGameLaunchRequest(
            action = intent.action,
            targetId = intent.getStringExtra(ExternalGameLaunchContract.EXTRA_TARGET_ID),
        )) {
            is ExternalGameLaunchRequestResult.Accepted -> {
                val request = requestCoordinator.replace(result.targetId)
                retryNotBeforeUptime = 0L
                retryArmed = false
                Log.i(TAG, "Accepted external target request generation=${request.generation}")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                timeoutHandler.postDelayed(timeoutRunnable, LIBRARY_TIMEOUT_MILLIS)
                resolvePendingRequest()
            }
            is ExternalGameLaunchRequestResult.Rejected -> finishWithError(result.reason)
        }
    }

    private fun resolvePendingRequest() {
        if (terminal) return
        val request = requestCoordinator.current() ?: return
        if (libraryState.loading) return
        libraryState.error?.let { error ->
            if (request.attempts == 0) {
                finishWithError("AdventurePad could not read the ScummVM game library: $error")
            } else {
                // The custom ScummVM build may restart its process while switching targets.
                // Keep this activity and its request alive while the signature-protected
                // service binding reconnects, then make the single bounded retry.
                libraryClient.connect()
            }
            return
        }
        if (!libraryState.connected) return

        val target = resolveExternalLaunchTarget(request.targetId, libraryState.targets)
        if (target == null) {
            finishWithError("No configured ScummVM game matches '${request.targetId}'.")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now < nextDispatchNotBeforeUptime) {
            timeoutHandler.removeCallbacks(retryRunnable)
            timeoutHandler.postAtTime(retryRunnable, nextDispatchNotBeforeUptime)
            return
        }
        if (request.attempts > 0) {
            if (!retryArmed) return
            if (now < retryNotBeforeUptime) {
                timeoutHandler.removeCallbacks(retryRunnable)
                timeoutHandler.postAtTime(retryRunnable, retryNotBeforeUptime)
                return
            }
        }
        val targetId = requestCoordinator.nextAttempt(
            generation = request.generation,
            maxAttempts = MAX_LAUNCH_ATTEMPTS,
        ) ?: return
        retryArmed = false
        if (!libraryClient.launch(targetId, ScummVMLaunchOrigin.EXTERNAL_LAUNCHER)) {
            Log.w(TAG, "ScummVM rejected launch attempt ${request.attempts + 1} for '$targetId'")
            return
        }
        terminal = true
        timeoutHandler.removeCallbacks(timeoutRunnable)
        val displayResult = DualDisplayCoordinator.restoreGameplayTrackpad(
            activity = this,
            reason = "External launcher dispatch accepted for configured target '$targetId'",
            externalLaunchTargetId = targetId,
        )
        if (displayResult.succeeded) {
            Log.i(TAG, "Dispatched configured target '$targetId'; ${displayResult.message}")
        } else {
            Log.w(TAG, "Dispatched configured target '$targetId'; ${displayResult.message}")
        }
        lifecycleScope.launch {
            launcherMetadataRepository.recordLaunch(targetId)
            finish()
        }
    }

    private fun finishWithError(message: String) {
        if (terminal) return
        terminal = true
        timeoutHandler.removeCallbacks(timeoutRunnable)
        Log.w(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        const val TAG = "AdventurePadExternal"
        const val LIBRARY_TIMEOUT_MILLIS = 12_000L
        const val MAX_LAUNCH_ATTEMPTS = 2
        const val PROCESS_RESTART_SETTLE_MILLIS = 2_500L
    }
}
