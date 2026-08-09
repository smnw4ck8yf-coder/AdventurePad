package com.jamesmoran.adventurepad

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkResolverInstrumentedTest {
    @Test
    fun exactTargetArtworkIsUsedWhenPresent() = runBlocking {
        val resolver = createResolver()
        val exact = requireNotNull(resolver.resolve(targetId = "indy3", gameId = "monkey"))
        val canonical = requireNotNull(createResolver().resolve(targetId = "missing-target", gameId = "monkey"))

        assertNotEquals(canonical.width, exact.width)
    }

    @Test
    fun canonicalGameArtworkIsUsedWhenTargetArtworkIsMissing() = runBlocking {
        val resolver = createResolver()

        assertNotNull(resolver.resolve(targetId = "indy3-fm", gameId = "indy3"))
    }

    @Test
    fun exactTargetArtworkWinsOverCanonicalArtwork() = runBlocking {
        val resolver = createResolver()
        val exact = requireNotNull(resolver.resolve(targetId = "indy3", gameId = "monkey"))
        val expectedExact = requireNotNull(createResolver().resolve(targetId = "indy3", gameId = "indy3"))

        assertEquals(expectedExact.width, exact.width)
        assertEquals(expectedExact.height, exact.height)
    }

    @Test
    fun missingTargetAndGameArtworkReturnsNull() = runBlocking {
        val resolver = createResolver()

        assertNull(resolver.resolve("missing-target", "missing-game"))
    }

    @Test
    fun identicalTargetAndGameIdResolvesNormally() = runBlocking {
        val resolver = createResolver()

        assertNotNull(resolver.resolve(targetId = "indy3", gameId = "indy3"))
    }

    @Test
    fun corruptTargetArtworkFallsBackToCanonicalArtwork() = runBlocking {
        val resolver = createResolver()
        val fallback = requireNotNull(
            resolver.resolve(targetId = "test-corrupt-target", gameId = "indy3"),
        )
        val canonical = requireNotNull(
            resolver.resolve(targetId = "another-missing-target", gameId = "indy3"),
        )

        assertSame(canonical, fallback)
    }

    @Test
    fun positiveAndNegativeCacheResultsRemainStable() = runBlocking {
        val resolver = createResolver()
        val firstCanonical = requireNotNull(resolver.resolve("variant-one", "indy3"))
        val secondCanonical = requireNotNull(resolver.resolve("variant-two", "indy3"))

        assertSame(firstCanonical, secondCanonical)
        assertNull(resolver.resolve("missing-target", "missing-game"))
        assertNull(resolver.resolve("missing-target", "missing-game"))
    }

    private fun createResolver(): ArtworkResolver {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ArtworkResolver.create(context, requestedWidthPx = 224)
    }
}
