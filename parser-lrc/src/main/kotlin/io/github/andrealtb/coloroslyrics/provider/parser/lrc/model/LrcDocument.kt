/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc.model

import kotlinx.serialization.Serializable

@Serializable
data class LrcDocument(
    val metadata: Map<String, String> = emptyMap(),
    val lines: List<LyricLine> = emptyList()
) {
    fun applyOffset(offsetMs: Long): LrcDocument {
        if (offsetMs == 0L) return this
        val newLines = lines.map { line ->
            val newBegin = (line.begin + offsetMs).coerceAtLeast(0L)
            val newEnd = (line.end + offsetMs).coerceAtLeast(newBegin)
            line.copy(begin = newBegin, end = newEnd, duration = newEnd - newBegin)
        }
        return copy(lines = newLines)
    }
}
