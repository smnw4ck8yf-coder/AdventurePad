package com.jamesmoran.adventurepad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Signature-protected entry point used by the custom ScummVM build after an explicit return. */
class ReturnToAdventurePadActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var homeHandoffAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_DESTINATION) == DESTINATION_ANDROID_HOME) {
            completeAndroidHomeHandoff()
            return
        }
        val result = DualDisplayCoordinator.restoreBoth(this)
        if (result.succeeded) {
            Log.i(TAG, result.message)
        } else {
            Log.e(TAG, result.message)
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun completeAndroidHomeHandoff() {
        when (val result = LowerDisplayHandoffCoordinator.backgroundLauncherTrackpad()) {
            LowerDisplayHandoffResult.Backgrounded,
            LowerDisplayHandoffResult.NoActiveTrackpad,
            -> navigateToAndroidHome()

            is LowerDisplayHandoffResult.RefusedGameplay -> {
                if (++homeHandoffAttempts < MAX_HOME_HANDOFF_ATTEMPTS) {
                    handler.postDelayed(::completeAndroidHomeHandoff, HOME_HANDOFF_RETRY_MILLIS)
                } else {
                    Log.e(TAG, "Timed out waiting for authoritative blank lower-display state")
                    navigateToAndroidHome()
                }
            }

            LowerDisplayHandoffResult.Failed -> {
                Log.e(TAG, "Could not background AdventurePad's lower-display task")
                navigateToAndroidHome()
            }
        }
    }

    private fun navigateToAndroidHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_DESTINATION = "com.jamesmoran.adventurepad.extra.RETURN_DESTINATION"
        const val DESTINATION_ANDROID_HOME = "ANDROID_HOME"

        const val TAG = "AdventurePadReturn"
        const val HOME_HANDOFF_RETRY_MILLIS = 50L
        const val MAX_HOME_HANDOFF_ATTEMPTS = 20
    }
}
