/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricLaneAlignmentPolicyTest {

    @Test
    fun enforcesMonotonicTimestamps() {
        val raw = listOf(
            RichLyricLine(begin = 2000L, end = 3000L, duration = 1000L, text = "Line 1"),
            RichLyricLine(begin = 1500L, end = 2500L, duration = 1000L, text = "Line 2")
        )

        val aligned = LyricLaneAlignmentPolicy.align(raw, filterPromo = false)
        assertEquals(2000L, aligned[0].begin)
        assertEquals(2000L, aligned[1].begin) // Clamped to monotonic previous begin
    }

    @Test
    fun filtersPromoLinesAndAlignsTranslations() {
        val source = listOf(
            RichLyricLine(begin = 1000L, end = 2000L, duration = 1000L, text = "Hello"),
            RichLyricLine(begin = 2500L, end = 3500L, duration = 1000L, text = "听歌就在中国酷狗星耀计划"),
            RichLyricLine(begin = 4000L, end = 5000L, duration = 1000L, text = "World")
        )

        val trans = listOf(
            RichLyricLine(begin = 1050L, end = 2000L, duration = 950L, text = "你好"),
            RichLyricLine(begin = 4020L, end = 5000L, duration = 980L, text = "世界")
        )

        val aligned = LyricLaneAlignmentPolicy.align(source, trans, filterPromo = true)
        assertEquals(2, aligned.size)
        assertEquals("Hello", aligned[0].text)
        assertEquals("你好", aligned[0].secondary)
        assertEquals("World", aligned[1].text)
        assertEquals("世界", aligned[1].secondary)
    }

    @Test
    fun ignoresEmptySlashTranslationMarker() {
        val source = listOf(RichLyricLine(begin = 1000L, end = 2000L, duration = 1000L, text = "Hello"))
        val trans = listOf(RichLyricLine(begin = 1000L, end = 2000L, duration = 1000L, text = "//"))

        val aligned = LyricLaneAlignmentPolicy.align(source, trans)
        assertNull(aligned[0].secondary)
    }
}
