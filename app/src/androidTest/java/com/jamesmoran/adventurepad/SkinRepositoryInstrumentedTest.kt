package com.jamesmoran.adventurepad

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkinRepositoryInstrumentedTest {
    private val targetId = "instrumentation-skin-target"
    private val repository by lazy {
        SkinRepository.get(ApplicationProvider.getApplicationContext())
    }

    @After
    fun clearAssignment() {
        repository.assignGameplaySkin(targetId, null)
    }

    @Test
    fun activePerTargetSkinReachesLowerGameplayResolutionAndRefreshesLive() {
        val initialRevision = repository.selectionRevision.value

        repository.assignGameplaySkin(targetId, BUILTIN_OCEAN_SKIN_ID)

        assertTrue(repository.selectionRevision.value > initialRevision)
        val resolved = repository.resolve(SkinContext.GAMEPLAY, targetId)
        assertEquals(BUILTIN_OCEAN_SKIN_ID, resolved.id)
        assertEquals(AdventurePadThemes.Ocean.id, resolved.theme.id)

        repository.assignGameplaySkin(targetId, null)

        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            repository.resolve(SkinContext.GAMEPLAY, targetId).id,
        )
    }

    @Test
    fun selectedAdventureColourThemeIsTheNoSkinGameplayFallback() {
        repository.assignGameplaySkin(targetId, null)

        val resolved = repository.resolve(
            SkinContext.GAMEPLAY,
            targetId,
            defaultGameplayTheme = AdventurePadThemes.Adventure,
        )

        assertEquals(BUILTIN_ADVENTURE_SKIN_ID, resolved.id)
        assertEquals("adventure", resolved.theme.id)
    }
}
