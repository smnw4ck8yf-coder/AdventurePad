package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkResolverTest {
    @Test
    fun targetIdMapsDirectlyToConventionBasedAssetPath() {
        assertEquals(
            "artwork/future-target/box.png",
            ArtworkResolver.artworkPath("future-target"),
        )
    }

    @Test
    fun targetArtworkPrecedesCanonicalGameArtwork() {
        assertEquals(
            listOf(
                "artwork/indy3-fm/box.png",
                "artwork/indy3/box.png",
            ),
            ArtworkResolver.artworkPaths(targetId = "indy3-fm", gameId = "indy3"),
        )
    }

    @Test
    fun identicalTargetAndGameIdsProduceOneCandidate() {
        assertEquals(
            listOf("artwork/indy3/box.png"),
            ArtworkResolver.artworkPaths(targetId = "indy3", gameId = "indy3"),
        )
    }

    @Test
    fun sampleSizeUsesPowersOfTwoWithoutUndersizing() {
        assertEquals(2, ArtworkResolver.calculateSampleSize(sourceWidth = 600, requestedWidth = 224))
        assertEquals(1, ArtworkResolver.calculateSampleSize(sourceWidth = 600, requestedWidth = 301))
    }
}
