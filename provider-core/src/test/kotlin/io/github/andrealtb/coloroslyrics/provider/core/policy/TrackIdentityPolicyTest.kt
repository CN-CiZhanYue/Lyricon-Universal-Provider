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

class TrackIdentityPolicyTest {

    @Test
    fun differentStableIdsNeverFallBackToMatchingTitleAndArtist() {
        val previous = TrackIdentity(id = "track-1", title = "Same", artist = "Artist")
        val current = TrackIdentity(id = "track-2", title = "Same", artist = "Artist")

        assertFalse(TrackIdentityPolicy.isSameTrack(previous, current))
        assertTrue(TrackIdentityPolicy.hasTrackChanged(previous, current))
    }

    @Test
    fun sameIdMatches() {
        val t1 = TrackIdentity(id = "12345", title = "Song A", artist = "Artist A")
        val t2 = TrackIdentity(id = "12345", title = "Different Title", artist = "Different Artist")
        assertTrue(TrackIdentityPolicy.isSameTrack(t1, t2))
    }

    @Test
    fun sameTitleAndArtistWithCloseDurationMatches() {
        val t1 = TrackIdentity(title = "Song A", artist = "Artist A", durationMs = 180000)
        val t2 = TrackIdentity(title = "song a  ", artist = "  artist a", durationMs = 181000)
        assertTrue(TrackIdentityPolicy.isSameTrack(t1, t2))
    }

    @Test
    fun differentArtistDoesNotMatch() {
        val t1 = TrackIdentity(title = "Song A", artist = "Artist A")
        val t2 = TrackIdentity(title = "Song A", artist = "Artist B")
        assertFalse(TrackIdentityPolicy.isSameTrack(t1, t2))
    }

    @Test
    fun mergePrefersAlreadyFilledFieldsAndFillsBlankAdamId() {
        val previous = TrackIdentity(title = "I Knew It", artist = "Taylor Swift")
        val incoming = TrackIdentity(
            id = "later-adam",
            title = "I Knew It",
            artist = "Taylor Swift",
            durationMs = 200_000L
        )
        val merged = TrackIdentityPolicy.mergePreferringFilled(previous, incoming)
        assertEquals("later-adam", merged.id)
        assertEquals("I Knew It", merged.title)
        assertEquals(200_000L, merged.durationMs)
    }

    @Test
    fun durationMismatchExceedingThresholdDoesNotMatch() {
        val t1 = TrackIdentity(title = "Song A", artist = "Artist A", durationMs = 180000)
        val t2 = TrackIdentity(title = "Song A", artist = "Artist A", durationMs = 210000)
        assertFalse(TrackIdentityPolicy.isSameTrack(t1, t2))
    }
}
