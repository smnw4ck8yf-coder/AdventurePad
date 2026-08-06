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

internal data class DisplayCandidate(
    val displayId: Int,
    val name: String,
    val isPresentationCategory: Boolean,
    val isValid: Boolean,
    val supportsPresentation: Boolean,
    val isPrivate: Boolean,
    val isAvailable: Boolean,
    val physicalWidth: Int,
    val physicalHeight: Int,
)

internal fun selectEligibleSecondaryDisplayId(candidates: List<DisplayCandidate>): Int? =
    candidates.firstOrNull { candidate ->
        val normalizedName = candidate.name.lowercase(Locale.ROOT)
        val looksVirtual = VirtualDisplayNameMarkers.any(normalizedName::contains)
        candidate.displayId != Display.DEFAULT_DISPLAY &&
            candidate.isPresentationCategory &&
            candidate.isValid &&
            candidate.supportsPresentation &&
            !candidate.isPrivate &&
            candidate.isAvailable &&
            !looksVirtual &&
            candidate.physicalWidth > 0 &&
            candidate.physicalHeight > 0
    }?.displayId

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
        val selectedDisplayId = selectEligibleSecondaryDisplayId(
            availableDisplays.map { candidate ->
                candidate.toDisplayCandidate(candidate.displayId in presentationDisplayIds)
            },
        )
        val selectedDisplay = availableDisplays.firstOrNull { it.displayId == selectedDisplayId }

        if (selectedDisplay != null) {
            Log.i(
                TAG,
                "Dynamically selected secondary display ${selectedDisplay.displayId} " +
                    "(${selectedDisplay.name}).",
            )
        }
        return selectedDisplay
    }

    private fun Display.toDisplayCandidate(isPresentationCategory: Boolean): DisplayCandidate {
        val currentMode = mode
        return DisplayCandidate(
            displayId = displayId,
            name = name,
            isPresentationCategory = isPresentationCategory,
            isValid = isValid,
            supportsPresentation = flags and Display.FLAG_PRESENTATION != 0,
            isPrivate = flags and Display.FLAG_PRIVATE != 0,
            isAvailable = state != Display.STATE_OFF && state != Display.STATE_UNKNOWN,
            physicalWidth = currentMode.physicalWidth,
            physicalHeight = currentMode.physicalHeight,
        )
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
}

private val VirtualDisplayNameMarkers = listOf(
    "virtual",
    "overlay",
    "screen record",
    "screenrecord",
    "screen share",
    "screenshare",
    "mirroring",
)

internal fun Int.toHexFlags(): String = String.format(Locale.US, "0x%08X", this)
