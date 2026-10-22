/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

/**
 * Apple / BetterLyrics TTML time syllables of one Latin word as adjacent
 * spans with no whitespace between them. Bridge then inserts a display
 * space between every adjacent ASCII timed segment, so "Galway" becomes
 * "Gal way" unless those spans are merged first.
 */
object LatinSyllableSpanMerger {
    data class Span(
        val text: String,
        val begin: Long,
        val end: Long,
        val hasTrailingSpace: Boolean
    )

    fun merge(spans: List<Span>): List<Span> {
        if (spans.size < 2) return spans
        val merged = mutableListOf<Span>()
        var pending: Span? = null

        fun flushPending() {
            pending?.let(merged::add)
            pending = null
        }

        spans.forEach { span ->
            if (!containsAsciiLetterOrDigit(span.text)) {
                flushPending()
                merged.add(span)
                return@forEach
            }
            pending = pending?.let { current ->
                current.copy(
                    text = current.text + span.text,
                    end = span.end.coerceAtLeast(current.end),
                    hasTrailingSpace = span.hasTrailingSpace
                )
            } ?: span
            if (span.hasTrailingSpace) {
                flushPending()
            }
        }
        flushPending()
        return merged
    }

    fun containsAsciiLetterOrDigit(value: String): Boolean =
        value.any { character ->
            character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9'
        }
}
