package com.jamesmoran.adventurepad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalGameReturnLifecycleInstrumentedTest {
    @Test
    fun confirmedExternalSessionSurvivesActivityRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, TrackpadActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DualDisplayCoordinator.EXTRA_SKIN_CONTEXT, SkinContext.GAMEPLAY.name)
            .putExtra(DualDisplayCoordinator.EXTRA_EXTERNAL_LAUNCH_SESSION, true)
            .putExtra(DualDisplayCoordinator.EXTRA_EXTERNAL_LAUNCH_TARGET, "atlantis")

        ActivityScenario.launch<TrackpadActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("atlantis", externalReturnTarget(activity))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertEquals("atlantis", externalReturnTarget(activity))
            }
        }
    }

    @Test
    fun explicitReturnIntentResolvesToSignatureProtectedEntryPoint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, ReturnToAdventurePadActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(component, 0)
        val intent = Intent().setComponent(component)

        assertEquals(component, intent.resolveActivity(context.packageManager))
        assertTrue(activityInfo.exported)
        assertEquals(
            "com.jamesmoran.adventurepad.permission.LAUNCH_TRACKPAD",
            activityInfo.permission,
        )
        assertTrue(activityInfo.flags and ActivityInfo.FLAG_NO_HISTORY != 0)
    }

    @Test
    fun launcherContextCancelsExternalReturnAndRemainsClearAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val externalIntent = Intent(context, TrackpadActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DualDisplayCoordinator.EXTRA_SKIN_CONTEXT, SkinContext.GAMEPLAY.name)
            .putExtra(DualDisplayCoordinator.EXTRA_EXTERNAL_LAUNCH_SESSION, true)
            .putExtra(DualDisplayCoordinator.EXTRA_EXTERNAL_LAUNCH_TARGET, "atlantis")

        ActivityScenario.launch<TrackpadActivity>(externalIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("atlantis", externalReturnTarget(activity))
                activity.startActivity(
                    Intent(activity, TrackpadActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(
                            DualDisplayCoordinator.EXTRA_SKIN_CONTEXT,
                            SkinContext.LAUNCHER.name,
                        ),
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity -> assertNull(externalReturnTarget(activity)) }
            scenario.recreate()
            scenario.onActivity { activity -> assertNull(externalReturnTarget(activity)) }
        }
    }

    @Test
    fun dialogSafePresentationRestoresSavedSplitWithoutChangingPreference() {
        val preferred = DisplayModePreferences(DisplayMode.INTERFACE)
        val split = InterfaceSplit(0.75f)
        val activeGeometry = MirrorSourceGeometry(
            width = 320,
            height = 200,
            rendererCapability = 1,
            generation = 7,
            gameId = "atlantis",
            guiOverlayVisible = true,
        )

        assertTrue(isDialogSafeOverlayActive(activeGeometry, connected = true))
        val duringDialog = resolvePresentationTarget(
            preferred.preferredMode,
            split,
            null,
            connected = true,
            activeSurfaceReady = true,
            guiOverlayVisible = true,
        )
        val restored = resolvePresentationTarget(
            preferred.preferredMode,
            split,
            null,
            connected = true,
            activeSurfaceReady = true,
            guiOverlayVisible = false,
        )

        assertEquals(PresentationOwner.DIALOG_SAFE, duringDialog.owner)
        assertFalse(duringDialog.runtimePanelRequested)
        assertEquals(PresentationOwner.SPLIT_VIEW, restored.owner)
        assertEquals(split.interfaceCrop, restored.lowerCrop)
        assertEquals(DisplayMode.INTERFACE, preferred.preferredMode)
        assertEquals(split.interfaceCrop, restored.upperCrop)
    }

    private fun externalReturnTarget(activity: TrackpadActivity): String? {
        val field = TrackpadActivity::class.java.getDeclaredField("externalGameReturnHandoff")
        field.isAccessible = true
        return (field.get(activity) as ExternalGameReturnHandoffStateMachine).currentExpectedTargetId()
    }
}
