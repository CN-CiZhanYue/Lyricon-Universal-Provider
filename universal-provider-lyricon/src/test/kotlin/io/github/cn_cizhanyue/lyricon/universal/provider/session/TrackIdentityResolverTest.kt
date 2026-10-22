/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackIdentityResolverTest {
    private val resolver = TrackIdentityResolver()

    @Test
    fun lyricLineChurnDoesNotCreateNewTrack() {
        val first = resolver.observe(saltLine("We're a wreck, you're the wreckin' ball\n我们破镜难圆 你是罪魁祸首"))
        val second = resolver.observe(saltLine("Second lyric line"))
        assertEquals(1L, first.descriptor.trackGeneration)
        assertEquals(1L, second.descriptor.trackGeneration)
        assertEquals("Babe (Taylor's Version) (From The Vault)", second.descriptor.title)
        assertEquals("Taylor Swift", second.descriptor.artist)
        assertTrue(second.descriptor.titlePolluted)
        assertNotEquals(first.descriptor.metadataRevision, second.descriptor.metadataRevision)
    }

    @Test
    fun pollutedSongSwitchWithSameArtistIncrementsGeneration() {
        val song1 = resolver.observe(
            observation(
                title = "Lyric line for Song 1",
                artist = "Taylor Swift - Song One",
                albumArtist = "Taylor Swift",
                album = "Same Album",
                durationMs = 200000L
            )
        )
        assertEquals(1L, song1.descriptor.trackGeneration)
        assertEquals("Song One", song1.descriptor.title)

        val song2 = resolver.observe(
            observation(
                title = "Lyric line for Song 2",
                artist = "Taylor Swift - Song Two",
                albumArtist = "Taylor Swift",
                album = "Same Album",
                durationMs = 210000L
            )
        )
        assertEquals(2L, song2.descriptor.trackGeneration)
        assertEquals("Song Two", song2.descriptor.title)
    }

    @Test
    fun pollutedToCleanMetadataKeepsGenerationAcrossAlbumTransient() {
        val polluted = resolver.observe(
            observation(
                title = "Current lyric line",
                artist = "Taylor Swift - Enchanted (Taylor's Version)",
                albumArtist = "Taylor Swift",
                album = "Speak Now (Taylor's Version)",
                durationMs = 231000L
            )
        )
        val clean = resolver.observe(
            observation(
                title = "Enchanted (Taylor's Version)",
                artist = "Taylor Swift",
                album = null,
                durationMs = 231000L
            )
        )
        assertTrue(polluted.descriptor.titlePolluted)
        assertEquals(1L, clean.descriptor.trackGeneration)
        assertEquals("Enchanted (Taylor's Version)", clean.descriptor.title)
    }

    @Test
    fun realTrackChangeIncrementsGeneration() {
        resolver.observe(saltLine("line-1"))
        val next = resolver.observe(
            observation(
                title = "All I Ask",
                artist = "Adele",
                album = "25",
                durationMs = 271000L
            )
        )
        assertEquals(2L, next.descriptor.trackGeneration)
        assertEquals("All I Ask", next.descriptor.title)
        assertFalse(next.descriptor.titlePolluted)
    }

    @Test
    fun independentSessionsKeepSeparateGenerations() {
        val salt = resolver.observe(saltLine("line-1"))
        val other = resolver.observe(
            observation(
                sessionInstanceId = "other",
                ownerPackage = "com.example.other",
                title = "Song B",
                artist = "Artist B",
                durationMs = 180000L
            )
        )
        assertEquals(1L, salt.descriptor.trackGeneration)
        assertEquals(1L, other.descriptor.trackGeneration)
        assertEquals("salt", salt.descriptor.sessionInstanceId)
        assertEquals("other", other.descriptor.sessionInstanceId)
    }

    @Test
    fun tornPayloadBeforeNextTrackCreatesOneGeneration() {
        resolver.observe(player("Song A", "Artist A", "Album A", 200_000L, at = 1_000L))
        // The previous track's delayed callback: its captured title with the new track's fields.
        val torn = resolver.observe(player("Song A", "Artist B", "Album B", 260_000L, at = 60_000L))
        assertEquals(1L, torn.descriptor.trackGeneration)
        assertEquals("Artist A", torn.descriptor.artist)
        assertEquals(TrackIdentityResolver.GATE_PARTIAL_PENDING, torn.identityGate)
        assertEquals(60_000L + TrackIdentityResolver.IDENTITY_SETTLE_MS, torn.identitySettleAtElapsedMs)

        val next = resolver.observe(player("Song B", "Artist B", "Album B", 260_000L, at = 60_400L))
        assertEquals(2L, next.descriptor.trackGeneration)
        assertEquals("Song B", next.descriptor.title)
        assertEquals(TrackIdentityResolver.GATE_STANDARD, next.identityGate)
        assertNull(next.identitySettleAtElapsedMs)
    }

    @Test
    fun staleTitleAfterNextTrackKeepsCurrentTrack() {
        resolver.observe(player("Song A", "Artist", "Album", 200_000L, at = 1_000L))
        val next = resolver.observe(player("Song B", "Artist", "Album", 260_000L, at = 60_000L))
        assertEquals(2L, next.descriptor.trackGeneration)

        val torn = resolver.observe(player("Song A", "Artist", "Album", 260_000L, at = 61_500L))
        assertEquals(2L, torn.descriptor.trackGeneration)
        assertEquals("Song B", torn.descriptor.title)
        assertEquals(TrackIdentityResolver.GATE_STALE_TITLE, torn.identityGate)

        // Playback-state updates keep re-reading the stored torn metadata after the window.
        val reread = resolver.observe(player("Song A", "Artist", "Album", 260_000L, at = 90_000L))
        assertEquals(2L, reread.descriptor.trackGeneration)
        assertEquals("Song B", reread.descriptor.title)
    }

    @Test
    fun returningToPreviousTitleIsNewTrackOutsideTheStaleWindow() {
        resolver.observe(player("Song A", "Artist", "Album", 200_000L, at = 1_000L))
        resolver.observe(player("Song B", "Artist", "Album", 200_500L, at = 60_000L))

        val back = resolver.observe(
            player("Song A", "Artist", "Album", 200_000L, at = 60_000L + TrackIdentityResolver.STALE_TITLE_WINDOW_MS + 1L)
        )
        assertEquals(3L, back.descriptor.trackGeneration)
        assertEquals("Song A", back.descriptor.title)
    }

    @Test
    fun returningToPreviousTrackWithItsOwnDurationIsNewTrack() {
        resolver.observe(player("Song A", "Artist", "Album", 200_000L, at = 1_000L))
        resolver.observe(player("Song B", "Artist", "Album", 260_000L, at = 60_000L))

        val back = resolver.observe(player("Song A", "Artist", "Album", 200_000L, at = 61_000L))
        assertEquals(3L, back.descriptor.trackGeneration)
        assertEquals("Song A", back.descriptor.title)
    }

    @Test
    fun unresolvedPartialChangeSettlesIntoNewTrack() {
        resolver.observe(player("Intro", "Artist A", "Album A", 90_000L, at = 1_000L))
        val held = resolver.observe(player("Intro", "Artist B", "Album B", 120_000L, at = 30_000L))
        assertEquals(1L, held.descriptor.trackGeneration)
        val settleAt = held.identitySettleAtElapsedMs!!

        val early = resolver.observe(held.observation.copy(observedAtElapsedMs = settleAt - 1L))
        assertEquals(1L, early.descriptor.trackGeneration)
        val settled = resolver.observe(held.observation.copy(observedAtElapsedMs = settleAt))
        assertEquals(2L, settled.descriptor.trackGeneration)
        assertEquals("Artist B", settled.descriptor.artist)
        assertEquals(TrackIdentityResolver.GATE_PARTIAL_SETTLED, settled.identityGate)
    }

    @Test
    fun durationArrivingAfterInitialMetadataKeepsGeneration() {
        resolver.observe(player("Song A", "Artist", "Album", null, at = 1_000L))
        val withDuration = resolver.observe(player("Song A", "Artist", "Album", 200_000L, at = 1_300L))
        assertEquals(1L, withDuration.descriptor.trackGeneration)
        assertEquals(200_000L, withDuration.descriptor.durationMs)
        assertEquals(TrackIdentityResolver.GATE_STANDARD, withDuration.identityGate)
    }

    private fun saltLine(title: String): SessionObservation = observation(
        title = title,
        artist = "Taylor Swift - Babe (Taylor's Version) (From The Vault)",
        albumArtist = "Taylor Swift",
        album = "Red (Taylor's Version) [24_96]",
        durationMs = 224240L
    )

    /** A player without media ID, queue ID or media URI, identified only by its text fields. */
    private fun player(title: String, artist: String, album: String, durationMs: Long?, at: Long): SessionObservation =
        observation(
            sessionInstanceId = "player",
            ownerPackage = "com.example.player",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            at = at
        )

    private fun observation(
        sessionInstanceId: String = "salt",
        ownerPackage: String = "com.salt.music",
        title: String,
        artist: String,
        albumArtist: String? = null,
        album: String? = null,
        durationMs: Long? = null,
        playing: Boolean = true,
        at: Long = 1_000L
    ): SessionObservation = SessionObservation(
        userId = 0,
        ownerPackage = ownerPackage,
        sessionInstanceId = sessionInstanceId,
        raw = RawSessionMetadata(
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            album = album,
            durationMs = durationMs
        ),
        playbackState = if (playing) PlaybackStates.PLAYING else PlaybackStates.PAUSED,
        playing = playing,
        observedAtElapsedMs = at
    )
}
