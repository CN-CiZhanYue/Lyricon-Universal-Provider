/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BluetoothLyricDemuxTest {

    @Test
    fun saltLiveDumpUsesAlbumArtistSubtraction() {
        val result = BluetoothLyricDemux.demux(
            RawSessionMetadata(
                title = "We're a wreck, you're the wreckin' ball\n我们破镜难圆 你是罪魁祸首",
                artist = "Taylor Swift - Babe (Taylor's Version) (From The Vault)",
                albumArtist = "Taylor Swift",
                album = "Red (Taylor's Version) [24_96]",
                durationMs = 224240L
            )
        )
        assertTrue(result.titlePolluted)
        assertEquals("Babe (Taylor's Version) (From The Vault)", result.title)
        assertEquals("Taylor Swift", result.artist)
        assertEquals("album-artist-prefix", result.source)
        assertEquals(224240L, result.durationMs)
    }

    @Test
    fun displayFieldsWinOverLyricTitle() {
        val result = BluetoothLyricDemux.demux(
            RawSessionMetadata(
                title = "Current lyric line",
                artist = "William Black/Fairlane - Broken",
                displayTitle = "Broken",
                displaySubtitle = "William Black/Fairlane",
                durationMs = 180000L
            )
        )
        assertTrue(result.titlePolluted)
        assertEquals("Broken", result.title)
        assertEquals("William Black/Fairlane", result.artist)
        assertEquals("display", result.source)
    }

    @Test
    fun ordinaryMetadataStaysOrdinary() {
        val result = BluetoothLyricDemux.demux(
            RawSessionMetadata(
                title = "All I Ask",
                artist = "Adele",
                album = "25",
                durationMs = 271000L
            )
        )
        assertFalse(result.titlePolluted)
        assertEquals("All I Ask", result.title)
        assertEquals("Adele", result.artist)
        assertEquals("standard", result.source)
    }

    @Test
    fun zeroDurationIsUnknown() {
        val result = BluetoothLyricDemux.demux(
            RawSessionMetadata(
                title = "Live",
                artist = "Artist",
                durationMs = 0L
            )
        )
        assertNull(result.durationMs)
    }
}