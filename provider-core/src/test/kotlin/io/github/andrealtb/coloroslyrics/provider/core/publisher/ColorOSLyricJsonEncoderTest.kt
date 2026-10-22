/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.EnhanceLrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorOSLyricJsonEncoderTest {

    @Test
    fun encodesColorOSOfficialJsonStructure() {
        val track = TrackIdentity(id = "1001", title = "Test Song", artist = "Test Artist", durationMs = 180000)
        val lines = listOf(
            RichLyricLine(
                begin = 1000L,
                end = 2000L,
                duration = 1000L,
                text = "Hello World",
                words = listOf(LyricWord(1000L, 1500L, 500L, "Hello "), LyricWord(1500L, 2000L, 500L, "World")),
                secondary = "你好世界"
            )
        )

        val encoded = ColorOSLyricJsonEncoder.encode(track, lines, trackGeneration = 5L, playerPackage = "com.test.player")
        assertNotNull(encoded)

        assertTrue(encoded.jsonValue.contains("\"songName\":\"Test Song\""))
        assertTrue(encoded.jsonValue.contains("\"artist\":\"Test Artist\""))
        assertTrue(encoded.jsonValue.contains("\"provider\":\"com.test.player\""))
        assertTrue(encoded.jsonValue.contains("\"sessionGeneration\":5"))
        assertTrue(encoded.jsonValue.contains("\"rawLyric\":"))
        assertTrue(encoded.jsonValue.contains("\"translationLyric\":"))
    }

    @Test
    fun bracketInlineTimingIsPlainForSystemUiAndEnhancedForBridge() {
        val lines = EnhanceLrcParser.parse(
            "[00:11.367]I [00:11.548]heard [00:11.773]you[00:12.062]"
        ).lines
        val encoded = ColorOSLyricJsonEncoder.encode(
            track = TrackIdentity(id = "salt-1", title = "Title", artist = "Artist"),
            lines = lines,
            trackGeneration = 1L,
            playerPackage = "com.salt.music"
        )

        assertNotNull(encoded)
        assertTrue(encoded.plainLyric.contains("[00:11.367]I heard you"))
        assertFalse(encoded.plainLyric.contains("[00:11.548]"))
        assertFalse(encoded.plainLyric.contains("[00:11.773]"))
        assertTrue(encoded.rawLyric.contains("<00:11.367>I "))
        assertTrue(encoded.rawLyric.contains("<00:11.548>heard "))
        assertTrue(encoded.rawLyric.contains("<00:11.773>you"))
        assertTrue(encoded.rawLyric.contains("<00:12.062>"))
    }
}
