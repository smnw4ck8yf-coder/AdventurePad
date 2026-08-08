package com.jamesmoran.adventurepad

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var lifecycleEvent by mutableStateOf("INITIALIZING")
    private var lastLaunchResult by mutableStateOf("Waiting for initial trackpad launch.")
    private var receivedIntentFlags by mutableStateOf(0)
    private var currentDisplayId by mutableStateOf(Display.INVALID_DISPLAY)
    private lateinit var themePreferencesRepository: ThemePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receivedIntentFlags = intent.flags
        currentDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        recordLifecycle("CREATED")
        enableEdgeToEdge()

        val displayManager = getSystemService(DisplayManager::class.java)
        themePreferencesRepository = ThemePreferencesRepository.create(this, lifecycleScope)

        setContent {
            val activeTheme by themePreferencesRepository.activeTheme.collectAsState()
            AdventurePadTheme(theme = activeTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DisplayInfoScreen(
                        heading = "TOP DISPLAY",
                        display = displayManager.getDisplay(currentDisplayId)
                            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY),
                        diagnostics = runtimeDiagnostics(),
                        onRestoreBothScreens = ::restoreBothScreens,
                    )
                    TopDisplayCursor()
                }
            }
        }

        lastLaunchResult = DualDisplayCoordinator.launchTrackpad(
            activity = this,
            reason = "Initial MainActivity launch",
        ).message
    }

    override fun onStart() {
        super.onStart()
        recordLifecycle("STARTED")
    }

    override fun onResume() {
        super.onResume()
        recordLifecycle("RESUMED")
    }

    override fun onPause() {
        recordLifecycle("PAUSED")
        super.onPause()
    }

    override fun onStop() {
        recordLifecycle("STOPPED")
        super.onStop()
    }

    override fun onDestroy() {
        recordLifecycle("DESTROYED")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedIntentFlags = intent.flags
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Received launch request: $it" }
            ?: "Received a new intent without a launch reason."
        recordLifecycle("NEW_INTENT")
    }

    private fun restoreBothScreens() {
        lastLaunchResult = "Restore in progress…"
        lastLaunchResult = DualDisplayCoordinator.restoreBoth(this).message
    }

    private fun runtimeDiagnostics() = ActivityRuntimeDiagnostics(
        displayId = currentDisplayId,
        taskId = taskId,
        isTaskRoot = isTaskRoot,
        lifecycleEvent = lifecycleEvent,
        intentFlags = receivedIntentFlags,
        lastResult = lastLaunchResult,
    )

    private fun recordLifecycle(event: String) {
        currentDisplayId = display?.displayId ?: currentDisplayId
        lifecycleEvent = event
        Log.i(
            TAG,
            "MainActivity $event displayId=$currentDisplayId " +
                "taskId=$taskId isTaskRoot=$isTaskRoot flags=${receivedIntentFlags.toHexFlags()}",
        )
    }

    private companion object {
        const val TAG = "AdventurePadLifecycle"
    }
}

@Composable
private fun TopDisplayCursor() {
    var cursorState by remember { mutableStateOf(TopCursorState()) }
    val cursorRadius = with(LocalDensity.current) { TopCursorRadius.toPx() }
    val cursorColor = AdventurePadThemeTokens.components.topCursor
    val cursorOutlineColor = AdventurePadThemeTokens.components.topCursorOutline

    DisposableEffect(cursorRadius) {
        val subscription = CursorDeltaCoordinator.subscribe { delta ->
            cursorState = cursorState.moveBy(delta.dx, delta.dy)
        }
        onDispose(subscription::cancel)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                cursorState = cursorState.withBounds(
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    radius = cursorRadius,
                )
            },
    ) {
        val cursorCenter = if (cursorState.initialized) {
            Offset(cursorState.x, cursorState.y)
        } else {
            center
        }
        drawCircle(
            color = cursorOutlineColor,
            radius = cursorRadius + TopCursorOutline.toPx(),
            center = cursorCenter,
        )
        drawCircle(
            color = cursorColor,
            radius = cursorRadius,
            center = cursorCenter,
        )
    }
}

private data class TopCursorState(
    val x: Float = 0f,
    val y: Float = 0f,
    val minimumX: Float = 0f,
    val maximumX: Float = 0f,
    val minimumY: Float = 0f,
    val maximumY: Float = 0f,
    val initialized: Boolean = false,
) {
    fun withBounds(width: Float, height: Float, radius: Float): TopCursorState {
        val horizontalInset = radius.coerceIn(0f, width.coerceAtLeast(0f) / 2f)
        val verticalInset = radius.coerceIn(0f, height.coerceAtLeast(0f) / 2f)
        val newMinimumX = horizontalInset
        val newMaximumX = (width - horizontalInset).coerceAtLeast(newMinimumX)
        val newMinimumY = verticalInset
        val newMaximumY = (height - verticalInset).coerceAtLeast(newMinimumY)
        return if (initialized) {
            copy(
                x = x.coerceIn(newMinimumX, newMaximumX),
                y = y.coerceIn(newMinimumY, newMaximumY),
                minimumX = newMinimumX,
                maximumX = newMaximumX,
                minimumY = newMinimumY,
                maximumY = newMaximumY,
            )
        } else {
            copy(
                x = width / 2f,
                y = height / 2f,
                minimumX = newMinimumX,
                maximumX = newMaximumX,
                minimumY = newMinimumY,
                maximumY = newMaximumY,
                initialized = width > 0f && height > 0f,
            )
        }
    }

    fun moveBy(dx: Float, dy: Float): TopCursorState = if (initialized) {
        copy(
            x = (x + dx).coerceIn(minimumX, maximumX),
            y = (y + dy).coerceIn(minimumY, maximumY),
        )
    } else {
        this
    }
}

internal data class ActivityRuntimeDiagnostics(
    val displayId: Int,
    val taskId: Int,
    val isTaskRoot: Boolean,
    val lifecycleEvent: String,
    val intentFlags: Int,
    val lastResult: String,
)

@Composable
internal fun ActivityDiagnosticsPanel(
    diagnostics: ActivityRuntimeDiagnostics,
    onRestoreBothScreens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Display ${diagnostics.displayId}  •  Task ${diagnostics.taskId}  •  " +
                "Root ${diagnostics.isTaskRoot}  •  ${diagnostics.lifecycleEvent}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Intent flags: ${diagnostics.intentFlags.toHexFlags()}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "Last result: ${diagnostics.lastResult}",
            color = AdventurePadThemeTokens.colors.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onRestoreBothScreens,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("RESTORE BOTH SCREENS")
        }
    }
}

@Composable
internal fun DisplayInfoScreen(
    heading: String,
    display: Display?,
    diagnostics: ActivityRuntimeDiagnostics,
    onRestoreBothScreens: () -> Unit,
) {
    val mode = display?.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AdventurePadThemeTokens.components.topDisplayBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            color = AdventurePadThemeTokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Display ID: ${display?.displayId ?: "unavailable"}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Name: ${display?.name ?: "unavailable"}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Resolution: ${mode?.let { "${it.physicalWidth} × ${it.physicalHeight}" } ?: "unavailable"}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Refresh rate: ${mode?.let { String.format(Locale.US, "%.2f Hz", it.refreshRate) } ?: "unavailable"}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        ActivityDiagnosticsPanel(
            diagnostics = diagnostics,
            onRestoreBothScreens = onRestoreBothScreens,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

private val TopCursorRadius = 14.dp
private val TopCursorOutline = 3.dp

@Preview(showBackground = true)
@Composable
private fun TopDisplayPreview() {
    AdventurePadTheme {
        DisplayInfoScreen(
            heading = "TOP DISPLAY",
            display = null,
            diagnostics = ActivityRuntimeDiagnostics(
                displayId = 0,
                taskId = 1,
                isTaskRoot = true,
                lifecycleEvent = "RESUMED",
                intentFlags = 0,
                lastResult = "Preview",
            ),
            onRestoreBothScreens = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OceanTopDisplayPreview() {
    AdventurePadTheme(theme = AdventurePadThemes.Ocean) {
        DisplayInfoScreen(
            heading = "TOP DISPLAY",
            display = null,
            diagnostics = ActivityRuntimeDiagnostics(
                displayId = 0,
                taskId = 1,
                isTaskRoot = true,
                lifecycleEvent = "RESUMED",
                intentFlags = 0,
                lastResult = "Ocean theme preview",
            ),
            onRestoreBothScreens = {},
        )
    }
}
