package com.jamesmoran.adventurepad

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Routes launcher requests to the independently recoverable secondary-display trackpad task. */
class LauncherRoutingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = DualDisplayCoordinator.launchTrackpad(
            activity = this,
            reason = "AdventurePad launcher request",
        )
        if (!result.succeeded) {
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
