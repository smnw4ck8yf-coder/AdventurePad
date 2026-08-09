package com.jamesmoran.adventurepad

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display

/** Routes the app icon to the top-display launcher and lower-display companion tasks. */
class LauncherRoutingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON, "AdventurePad launcher request")
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()
        startActivity(launcherIntent, options)
        finish()
    }
}
