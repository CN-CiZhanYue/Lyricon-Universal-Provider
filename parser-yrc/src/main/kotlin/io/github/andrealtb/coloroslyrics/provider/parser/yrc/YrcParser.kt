/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.yrc

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import java.util.regex.Pattern

object YrcParser {
    private val YRC_LINE_HEADER_REGEX = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(\\d+)\\s*]")
    private val YRC_SYLLABLE_REGEX = Pattern.compile("\\(\\s*(-?\\d+)\\s*,\\s*(\\d+)\\s*,\\s*\\d+\\s*\\)([^()]*)")

    fun parse(raw: String?): List<RichLyricLine> {
        val entries = mutableListOf<RichLyricLine>()
        if (raw.isNullOrBlank()) return entries

        raw.lineSequence().forEach { line ->
            val trimLine = line.trim()
            if (trimLine.isBlank() || trimLine.startsWith("{")) return@forEach

            val headerMatcher = YRC_LINE_HEADER_REGEX.matcher(trimLine)
            if (headerMatcher.find()) {
                val lineStart = headerMatcher.group(1)?.toLongOrNull() ?: 0L
                val lineDuration = headerMatcher.group(2)?.toLongOrNull() ?: 0L
                val lineEnd = lineStart + lineDuration

                val words = mutableListOf<LyricWord>()
                val contentPart = trimLine.substring(headerMatcher.end())
                val wordMatcher = YRC_SYLLABLE_REGEX.matcher(contentPart)

                while (wordMatcher.find()) {
                    val start = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                    val duration = wordMatcher.group(2)?.toLongOrNull() ?: 0L
                    val text = wordMatcher.group(3) ?: ""

                    if (text.isEmpty()) continue

                    words.add(
                        LyricWord(
                            begin = start,
                            end = start + duration,
                            duration = duration,
                            text = text
                        )
                    )
                }

                val safeLineBegin = lineStart.coerceAtLeast(0L)
                val safeLineDuration = lineDuration.coerceAtLeast(0L)
                val safeLineEnd = (safeLineBegin + safeLineDuration).coerceAtLeast(safeLineBegin)
                val sorted = words
                    .map { word ->
                        val clampedBegin = word.begin.coerceAtLeast(safeLineBegin)
                        val clampedDuration = word.duration.coerceAtLeast(0L)
                        word.copy(
                            begin = clampedBegin,
                            duration = clampedDuration,
                            end = (clampedBegin + clampedDuration).coerceAtLeast(clampedBegin)
                        )
                    }
                    .sortedBy { it.begin }
                entries.add(
                    RichLyricLine(
                        begin = safeLineBegin,
                        end = safeLineEnd,
                        duration = safeLineDuration,
                        text = sorted.joinToString("") { it.text.orEmpty() },
                        words = sorted
                    )
                )
            }
        }

        return entries.sortedBy { it.begin }
    }
}
