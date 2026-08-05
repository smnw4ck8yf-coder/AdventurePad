package com.jamesmoran.adventurepad

import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme

class TrackpadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val displayManager = getSystemService(DisplayManager::class.java)
        val trackpadDisplay = display?.let { displayManager.getDisplay(it.displayId) ?: it }

        setContent {
            AdventurePadTheme {
                DisplayInfoScreen(
                    heading = "TRACKPAD DISPLAY",
                    display = trackpadDisplay,
                    backgroundColor = Color(0xFF6B1D2A),
                )
            }
        }
    }
}
