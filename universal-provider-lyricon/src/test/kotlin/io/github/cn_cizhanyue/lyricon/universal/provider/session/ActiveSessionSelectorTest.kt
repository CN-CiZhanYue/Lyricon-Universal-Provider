/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveSessionSelectorTest {
    private val resolver = TrackIdentityResolver()
    private val selector = ActiveSessionSelector(hysteresisMs = 500L)

    @Test
    fun pauseKeepsCurrentSelectionUntilAnotherSessionPlays() {
        val playing = resolve("a", playing = true, title = "Song A", artist = "Artist A")
        selector.select(listOf(playing), nowElapsedMs = 0L)
        val paused = resolve("a", playing = false, title = "Song A", artist = "Artist A")
        val stillSelected = selector.select(listOf(paused), nowElapsedMs = 100L)
        assertEquals("a", stillSelected?.descriptor?.sessionInstanceId)
        val otherPlaying = resolve("b", playing = true, title = "Song B", artist = "Artist B")
        val switched = selector.select(listOf(paused, otherPlaying), nowElapsedMs = 800L)
        assertEquals("b", switched?.descriptor?.sessionInstanceId)
    }

    @Test
    fun bufferingDoesNotStealPlayingSession() {
        val playing = resolve("a", playing = true, title = "Song A", artist = "Artist A")
        selector.select(listOf(playing), nowElapsedMs = 0L)
        val buffering = resolve(
            "b",
            playing = false,
            title = "Song B",
            artist = "Artist B",
            state = PlaybackStates.BUFFERING
        )
        val selected = selector.select(listOf(playing, buffering), nowElapsedMs = 100L)
        assertEquals("a", selected?.descriptor?.sessionInstanceId)
    }

    @Test
    fun twoPlayingSessionsWithoutPinAreAmbiguous() {
        val first = resolve("a", playing = true, title = "Song A", artist = "Artist A")
        val second = resolve("b", playing = true, title = "Song B", artist = "Artist B")
        val selected = selector.select(listOf(first, second), nowElapsedMs = 0L)
        assertNull(selected)
    }

    private fun resolve(
        id: String,
        playing: Boolean,
        title: String,
        artist: String,
        state: Int? = if (playing) PlaybackStates.PLAYING else PlaybackStates.PAUSED
    ): ResolvedSession {
        return resolver.observe(
            SessionObservation(
                userId = 0,
                ownerPackage = "pkg.$id",
                sessionInstanceId = id,
                raw = RawSessionMetadata(title = title, artist = artist, durationMs = 180000L),
                playbackState = state,
                playing = playing,
                observedAtElapsedMs = 1_000L
            )
        )
    }
}