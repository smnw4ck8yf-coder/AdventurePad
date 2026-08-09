package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Test

class ScummVMLibraryTest {
    @Test
    fun normalizationFiltersInvalidTargetsDeduplicatesAndSorts() {
        val targets = listOf(
            ScummVMTarget("samnmax", "Sam & Max", "scumm", "samnmax"),
            ScummVMTarget("monkey", "Monkey Island", "scumm", "monkey"),
            ScummVMTarget("samnmax", "Duplicate", "scumm", "samnmax"),
            ScummVMTarget("", "Invalid", "scumm", "invalid"),
            ScummVMTarget("broken", "Broken", "", ""),
        )

        assertEquals(
            listOf("monkey", "samnmax"),
            normalizeScummVMTargets(targets).map(ScummVMTarget::targetId),
        )
    }
}
