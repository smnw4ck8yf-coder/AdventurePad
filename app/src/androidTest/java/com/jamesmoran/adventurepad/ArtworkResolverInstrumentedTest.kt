package com.jamesmoran.adventurepad

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ArtworkResolverInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val customArtworkRepository = CustomArtworkRepository.create(context)

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

    @Test
    fun customTargetArtworkTakesPrecedenceOverBundledArtwork() = runBlocking {
        val targetId = "instrumentation-custom-precedence"
        try {
            customArtworkRepository.import(targetId, testImage(width = 73, height = 91))
            val artwork = requireNotNull(createResolver().resolve(targetId, "indy3"))

            assertEquals(73, artwork.width)
            assertEquals(91, artwork.height)
        } finally {
            customArtworkRepository.remove(targetId)
        }
    }

    @Test
    fun removingCustomArtworkRestoresBundledArtwork() = runBlocking {
        val targetId = "instrumentation-custom-removal"
        val resolver = createResolver()
        try {
            customArtworkRepository.import(targetId, testImage(width = 73, height = 91))
            assertEquals(73, requireNotNull(resolver.resolve(targetId, "indy3")).width)

            assertEquals(true, customArtworkRepository.remove(targetId))
            resolver.invalidateCustomArtwork(targetId)
            val fallback = requireNotNull(resolver.resolve(targetId, "indy3"))

            assertEquals(300, fallback.width)
            assertEquals(384, fallback.height)
        } finally {
            customArtworkRepository.remove(targetId)
        }
    }

    @Test
    fun invalidImportIsRejectedWithoutReplacingExistingArtwork() = runBlocking {
        val targetId = "instrumentation-invalid-import"
        try {
            customArtworkRepository.import(targetId, testImage(width = 73, height = 91))

            assertThrows(CustomArtworkException::class.java) {
                runBlocking {
                    customArtworkRepository.import(
                        targetId,
                        ByteArrayInputStream("not an image".toByteArray()),
                    )
                }
            }

            assertEquals(73, requireNotNull(createResolver().resolve(targetId, "indy3")).width)
        } finally {
            customArtworkRepository.remove(targetId)
        }
    }

    @Test
    fun corruptCustomArtworkFallsBackToBundledArtwork() = runBlocking {
        val targetId = "instrumentation-corrupt-custom"
        val customFile = customArtworkRepository.artworkFile(targetId)
        try {
            customFile.parentFile?.mkdirs()
            customFile.writeText("not an image")

            val fallback = requireNotNull(createResolver().resolve(targetId, "indy3"))

            assertEquals(300, fallback.width)
            assertEquals(384, fallback.height)
        } finally {
            customArtworkRepository.remove(targetId)
        }
    }

    private fun createResolver(): ArtworkResolver {
        return ArtworkResolver.create(
            context,
            requestedWidthPx = 224,
            customArtworkRepository = customArtworkRepository,
        )
    }

    private fun testImage(width: Int, height: Int): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).run {
                eraseColor(android.graphics.Color.MAGENTA)
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
            output.toByteArray()
        }
        return ByteArrayInputStream(bytes)
    }
}
