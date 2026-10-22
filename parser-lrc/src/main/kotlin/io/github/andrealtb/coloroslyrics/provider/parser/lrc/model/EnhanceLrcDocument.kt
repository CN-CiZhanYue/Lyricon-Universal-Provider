/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc.model

import kotlinx.serialization.Serializable

@Serializable
data class EnhanceLrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<RichLyricLine> = emptyList()
) {
    fun applyOffset(offsetMs: Long): EnhanceLrcDocument {
        if (offsetMs == 0L) return this
        val newLines = lines.map { line ->
            val newBegin = (line.begin + offsetMs).coerceAtLeast(0L)
            val newEnd = (line.end + offsetMs).coerceAtLeast(newBegin)
            val newWords = line.words?.map { word ->
                val wBegin = (word.begin + offsetMs).coerceAtLeast(0L)
                val wEnd = (word.end + offsetMs).coerceAtLeast(wBegin)
                word.copy(begin = wBegin, end = wEnd, duration = wEnd - wBegin)
            }
            val newSecondaryWords = line.secondaryWords?.map { word ->
                val wBegin = (word.begin + offsetMs).coerceAtLeast(0L)
                val wEnd = (word.end + offsetMs).coerceAtLeast(wBegin)
                word.copy(begin = wBegin, end = wEnd, duration = wEnd - wBegin)
            }
            line.copy(
                begin = newBegin,
                end = newEnd,
                duration = newEnd - newBegin,
                words = newWords,
                secondaryWords = newSecondaryWords
            )
        }
        return copy(lines = newLines)
    }
}
