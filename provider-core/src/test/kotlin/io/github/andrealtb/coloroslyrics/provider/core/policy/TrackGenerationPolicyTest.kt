/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackGenerationPolicyTest {

    @Test
    fun generationIncrementsOnNewTrack() {
        val policy = TrackGenerationPolicy()
        assertEquals(0L, policy.generation)

        val t1 = TrackIdentity(id = "1", title = "Track 1", artist = "Artist 1")
        val gen1 = policy.onTrackObserved(t1)
        assertEquals(1L, gen1)
        assertTrue(policy.isGenerationValid(1L))
        assertFalse(policy.isGenerationValid(0L))

        // Same track should not increment generation
        val genSame = policy.onTrackObserved(t1)
        assertEquals(1L, genSame)

        // New track increments generation
        val t2 = TrackIdentity(id = "2", title = "Track 2", artist = "Artist 2")
        val gen2 = policy.onTrackObserved(t2)
        assertEquals(2L, gen2)
        assertTrue(policy.isGenerationValid(2L))
        assertFalse(policy.isGenerationValid(1L))
    }

    @Test
    fun sameTrackLaterAdamIdDoesNotBumpGeneration() {
        val policy = TrackGenerationPolicy()
        val titleThenId = TrackIdentity(title = "I Knew It", artist = "Taylor Swift")
        val withId = titleThenId.copy(id = "later-adam")
        val genTitle = policy.onTrackObserved(titleThenId)
        assertEquals(1L, genTitle)
        val genMerged = policy.onTrackObserved(withId)
        assertEquals(1L, genMerged)
        assertEquals("later-adam", policy.currentTrack?.id)
        assertTrue(policy.isGenerationValid(1L))
    }
}
