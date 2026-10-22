/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.core.model.LyricTimingType
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine

object LyricTimingClassifier {

    fun classify(raw: String?, lines: List<RichLyricLine>): LyricTimingType {
        if (lines.isEmpty()) {
            if (raw.isNullOrBlank()) return LyricTimingType.INVALID
            return LyricTimingType.UNTIMED_TEXT
        }

        val hasValidTimestamps = lines.any { it.begin > 0L || it.end > 0L }
        if (!hasValidTimestamps) {
            return LyricTimingType.UNTIMED_TEXT
        }

        val hasWordTimings = lines.any { line ->
            val words = line.words
            !words.isNullOrEmpty() && words.any { (it.duration > 0 || it.end > it.begin) }
        }

        return if (hasWordTimings) {
            LyricTimingType.WORD
        } else {
            LyricTimingType.LINE
        }
    }
}
