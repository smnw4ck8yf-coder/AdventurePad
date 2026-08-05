package com.jamesmoran.adventurepad

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import java.util.Locale

internal data class DisplayLaunchResult(
    val succeeded: Boolean,
    val message: String,
)

internal object DualDisplayCoordinator {
    const val EXTRA_LAUNCH_REASON = "com.jamesmoran.adventurepad.LAUNCH_REASON"

    private const val TAG = "AdventurePadTasks"
    // NEW_TASK routes each singleTask activity to its declared affinity without duplicating tasks.
    private const val TASK_LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK

    fun launchTrackpad(activity: Activity, reason: String): DisplayLaunchResult {
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val secondaryDisplay = findEligibleSecondaryDisplay(displayManager)
            ?: return DisplayLaunchResult(
                succeeded = false,
                message = "ERROR: No eligible physical presentation display was found.",
            )
        val trackpadIntent = activityIntent<TrackpadActivity>(activity, reason)
        val permissionError = checkLaunchAllowed(
            activity = activity,
            displayId = secondaryDisplay.displayId,
            intent = trackpadIntent,
            activityName = "TrackpadActivity",
        )
        if (permissionError != null) return permissionError

        return startOnDisplay(
            activity = activity,
            intent = trackpadIntent,
            displayId = secondaryDisplay.displayId,
            successMessage = "TrackpadActivity launched/restored on display " +
                "${secondaryDisplay.displayId} in its dedicated task.",
        )
    }

    fun restoreBoth(activity: Activity): DisplayLaunchResult {
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val secondaryDisplay = findEligibleSecondaryDisplay(displayManager)
            ?: return DisplayLaunchResult(
                succeeded = false,
                message = "ERROR: Restore failed; no eligible secondary display was found.",
            )
        val mainIntent = activityIntent<MainActivity>(activity, "Restore requested")
        val trackpadIntent = activityIntent<TrackpadActivity>(activity, "Restore requested")

        checkLaunchAllowed(
            activity = activity,
            displayId = Display.DEFAULT_DISPLAY,
            intent = mainIntent,
            activityName = "MainActivity",
        )?.let { return it }
        checkLaunchAllowed(
            activity = activity,
            displayId = secondaryDisplay.displayId,
            intent = trackpadIntent,
            activityName = "TrackpadActivity",
        )?.let { return it }

        val mainResult = startOnDisplay(
            activity = activity,
            intent = mainIntent,
            displayId = Display.DEFAULT_DISPLAY,
            successMessage = "MainActivity restored on display ${Display.DEFAULT_DISPLAY}.",
        )
        if (!mainResult.succeeded) return mainResult

        val trackpadResult = startOnDisplay(
            activity = activity,
            intent = trackpadIntent,
            displayId = secondaryDisplay.displayId,
            successMessage = "TrackpadActivity restored on display ${secondaryDisplay.displayId}.",
        )
        if (!trackpadResult.succeeded) {
            return DisplayLaunchResult(
                succeeded = false,
                message = "PARTIAL RESTORE: ${mainResult.message} ${trackpadResult.message}",
            )
        }

        return DisplayLaunchResult(
            succeeded = true,
            message = "RESTORE REQUESTED: MainActivity → display 0; " +
                "TrackpadActivity → display ${secondaryDisplay.displayId}.",
        )
    }

    private fun findEligibleSecondaryDisplay(displayManager: DisplayManager): Display? {
        val availableDisplays = displayManager.displays
        availableDisplays.forEach { candidate ->
            Log.i(
                TAG,
                "Display ${candidate.displayId}: name=${candidate.name}, " +
                    "flags=${candidate.flags}, state=${candidate.state}",
            )
        }

        val presentationDisplayIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .mapTo(mutableSetOf()) { it.displayId }
        val selectedDisplay = availableDisplays.firstOrNull { candidate ->
            isEligibleSecondaryDisplay(candidate, presentationDisplayIds)
        }

        if (selectedDisplay != null) {
            Log.i(
                TAG,
                "Expected AYN Thor secondary display ID is 4; dynamically selected " +
                    "display ${selectedDisplay.displayId} (${selectedDisplay.name}).",
            )
        }
        return selectedDisplay
    }

    private fun isEligibleSecondaryDisplay(
        candidate: Display,
        presentationDisplayIds: Set<Int>,
    ): Boolean {
        val mode = candidate.mode
        val normalizedName = candidate.name.lowercase(Locale.ROOT)
        val looksVirtual = VirtualDisplayNameMarkers.any(normalizedName::contains)
        return candidate.displayId != Display.DEFAULT_DISPLAY &&
            candidate.displayId in presentationDisplayIds &&
            candidate.isValid &&
            candidate.flags and Display.FLAG_PRESENTATION != 0 &&
            candidate.flags and Display.FLAG_PRIVATE == 0 &&
            candidate.state != Display.STATE_OFF &&
            candidate.state != Display.STATE_UNKNOWN &&
            !looksVirtual &&
            mode.physicalWidth > 0 &&
            mode.physicalHeight > 0
    }

    private fun checkLaunchAllowed(
        activity: Activity,
        displayId: Int,
        intent: Intent,
        activityName: String,
    ): DisplayLaunchResult? {
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        val allowed = try {
            activityManager.isActivityStartAllowedOnDisplay(activity, displayId, intent)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Could not check launch permission for $activityName", exception)
            return DisplayLaunchResult(
                succeeded = false,
                message = "ERROR: Could not check $activityName launch permission: " +
                    (exception.message ?: exception.javaClass.simpleName),
            )
        }

        return if (allowed) {
            null
        } else {
            DisplayLaunchResult(
                succeeded = false,
                message = "ERROR: Android denied launching $activityName on display $displayId.",
            )
        }
    }

    private fun startOnDisplay(
        activity: Activity,
        intent: Intent,
        displayId: Int,
        successMessage: String,
    ): DisplayLaunchResult = try {
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
        activity.startActivity(intent, options)
        Log.i(TAG, "$successMessage flags=${intent.flags.toHexFlags()}")
        DisplayLaunchResult(succeeded = true, message = successMessage)
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Launch failed on display $displayId", exception)
        DisplayLaunchResult(
            succeeded = false,
            message = "ERROR: Launch failed on display $displayId: " +
                (exception.message ?: exception.javaClass.simpleName),
        )
    }

    private inline fun <reified T : Activity> activityIntent(
        activity: Activity,
        reason: String,
    ): Intent = Intent(activity, T::class.java)
        .addFlags(TASK_LAUNCH_FLAGS)
        .putExtra(EXTRA_LAUNCH_REASON, reason)

    private val VirtualDisplayNameMarkers = listOf(
        "virtual",
        "overlay",
        "screen record",
        "screenrecord",
        "screen share",
        "screenshare",
        "mirroring",
    )
}

internal fun Int.toHexFlags(): String = String.format(Locale.US, "0x%08X", this)
