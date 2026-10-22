/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.core.model.LyricTimingType
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import org.junit.Test
import kotlin.test.assertEquals

class LyricTimingClassifierTest {

    @Test
    fun classifiesWordLevelTiming() {
        val lines = listOf(
            RichLyricLine(
                begin = 1000L,
                end = 2000L,
                duration = 1000L,
                words = listOf(LyricWord(1000L, 1500L, 500L, "Hello"), LyricWord(1500L, 2000L, 500L, "World"))
            )
        )
        assertEquals(LyricTimingType.WORD, LyricTimingClassifier.classify("[00:01.00]Hello World", lines))
    }

    @Test
    fun classifiesLineLevelTiming() {
        val lines = listOf(
            RichLyricLine(begin = 1000L, end = 2000L, duration = 1000L, text = "Hello World", words = null)
        )
        assertEquals(LyricTimingType.LINE, LyricTimingClassifier.classify("[00:01.00]Hello World", lines))
    }

    @Test
    fun classifiesUntimedText() {
        val lines = listOf(
            RichLyricLine(begin = 0L, end = 0L, duration = 0L, text = "Static text line")
        )
        assertEquals(LyricTimingType.UNTIMED_TEXT, LyricTimingClassifier.classify("Static text line", lines))
    }

    @Test
    fun classifiesInvalidOnEmpty() {
        assertEquals(LyricTimingType.INVALID, LyricTimingClassifier.classify("", emptyList()))
    }
}
