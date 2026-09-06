package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherRoutingTest {
    @Test
    fun activeTargetRoutesAndroidLaunchToGameplayTrackpad() {
        assertEquals(
            AndroidLaunchDestination.GAMEPLAY_TRACKPAD,
            androidLaunchDestination("monkey"),
        )
    }

    @Test
    fun blankTargetRoutesAndroidLaunchToNormalLauncher() {
        assertEquals(AndroidLaunchDestination.LAUNCHER, androidLaunchDestination(""))
        assertEquals(AndroidLaunchDestination.LAUNCHER, androidLaunchDestination("   "))
        assertEquals(AndroidLaunchDestination.LAUNCHER, androidLaunchDestination(null))
    }
}
