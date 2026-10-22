/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LatinSyllableSpanMergerTest {

    @Test
    fun mergesUnspacedLatinSyllablesIntoOneWord() {
        val merged = LatinSyllableSpanMerger.merge(
            listOf(
                span("Gal", 0, 180, trailing = false),
                span("way", 180, 420, trailing = true),
                span("Girl", 450, 800, trailing = false)
            )
        )
        assertEquals(listOf("Galway", "Girl"), merged.map { it.text })
        assertEquals(0L, merged[0].begin)
        assertEquals(420L, merged[0].end)
        assertEquals(true, merged[0].hasTrailingSpace)
    }

    @Test
    fun keepsWordsSeparatedByTrailingSpace() {
        val merged = LatinSyllableSpanMerger.merge(
            listOf(
                span("Hello", 0, 300, trailing = true),
                span("World", 320, 800, trailing = false)
            )
        )
        assertEquals(listOf("Hello", "World"), merged.map { it.text })
    }

    @Test
    fun doesNotMergeAdjacentCjkSpans() {
        val merged = LatinSyllableSpanMerger.merge(
            listOf(
                span("你", 0, 200, trailing = false),
                span("好", 200, 400, trailing = false)
            )
        )
        assertEquals(listOf("你", "好"), merged.map { it.text })
    }

    private fun span(
        text: String,
        begin: Long,
        end: Long,
        trailing: Boolean
    ) = LatinSyllableSpanMerger.Span(text, begin, end, trailing)
}
