package com.jamesmoran.adventurepad

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var launchStatus by mutableStateOf("Searching for a secondary display…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val displayManager = getSystemService(DisplayManager::class.java)
        val mainDisplay = display ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)

        setContent {
            AdventurePadTheme {
                DisplayInfoScreen(
                    heading = "TOP DISPLAY",
                    display = mainDisplay,
                    backgroundColor = TopDisplayBackground,
                    status = launchStatus,
                )
            }
        }

        launchTrackpadOnSecondaryDisplay(displayManager)
    }

    private fun launchTrackpadOnSecondaryDisplay(displayManager: DisplayManager) {
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
        val secondaryDisplay = availableDisplays.firstOrNull { candidate ->
            isEligibleSecondaryDisplay(candidate, presentationDisplayIds)
        }
        if (secondaryDisplay == null) {
            launchStatus = "ERROR: No eligible physical presentation display was found."
            Log.e(TAG, launchStatus)
            return
        }

        Log.i(
            TAG,
            "Expected AYN Thor secondary display ID is 4; dynamically selected " +
                "display ${secondaryDisplay.displayId} (${secondaryDisplay.name}).",
        )

        val trackpadIntent = Intent(this, TrackpadActivity::class.java)
        val activityManager = getSystemService(ActivityManager::class.java)
        val launchAllowed = try {
            activityManager.isActivityStartAllowedOnDisplay(
                this,
                secondaryDisplay.displayId,
                trackpadIntent,
            )
        } catch (exception: RuntimeException) {
            launchStatus = "ERROR: Could not check display launch permission: " +
                (exception.message ?: exception.javaClass.simpleName)
            Log.e(TAG, launchStatus, exception)
            return
        }

        if (!launchAllowed) {
            launchStatus = "ERROR: Android denied launching TrackpadActivity on display " +
                "${secondaryDisplay.displayId}."
            Log.e(TAG, launchStatus)
            return
        }

        try {
            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(secondaryDisplay.displayId)
                .toBundle()
            startActivity(trackpadIntent, options)
            launchStatus = "LAUNCHED: TrackpadActivity on display ${secondaryDisplay.displayId}."
            Log.i(TAG, launchStatus)
        } catch (exception: RuntimeException) {
            launchStatus = "ERROR: Failed to launch TrackpadActivity on display " +
                "${secondaryDisplay.displayId}: " +
                (exception.message ?: exception.javaClass.simpleName)
            Log.e(TAG, launchStatus, exception)
        }
    }

    private fun isEligibleSecondaryDisplay(
        candidate: Display,
        presentationDisplayIds: Set<Int>,
    ): Boolean {
        val mode = candidate.mode
        val normalizedName = candidate.name.lowercase(Locale.ROOT)
        val looksVirtual = VIRTUAL_DISPLAY_NAME_MARKERS.any(normalizedName::contains)

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

    private companion object {
        const val TAG = "AdventurePadDisplays"

        val VIRTUAL_DISPLAY_NAME_MARKERS = listOf(
            "virtual",
            "overlay",
            "screen record",
            "screenrecord",
            "screen share",
            "screenshare",
            "mirroring",
        )
    }
}

@Composable
internal fun DisplayInfoScreen(
    heading: String,
    display: Display?,
    backgroundColor: Color,
    status: String? = null,
) {
    val mode = display?.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Display ID: ${display?.displayId ?: "unavailable"}",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Name: ${display?.name ?: "unavailable"}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Resolution: ${mode?.let { "${it.physicalWidth} × ${it.physicalHeight}" } ?: "unavailable"}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Refresh rate: ${mode?.let { String.format(Locale.US, "%.2f Hz", it.refreshRate) } ?: "unavailable"}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        status?.let {
            Text(
                text = it,
                color = Color(0xFFFFD166),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}

private val TopDisplayBackground = Color(0xFF102A43)

@Preview(showBackground = true)
@Composable
private fun TopDisplayPreview() {
    AdventurePadTheme {
        DisplayInfoScreen(
            heading = "TOP DISPLAY",
            display = null,
            backgroundColor = TopDisplayBackground,
            status = "Preview: waiting for a secondary display.",
        )
    }
}
