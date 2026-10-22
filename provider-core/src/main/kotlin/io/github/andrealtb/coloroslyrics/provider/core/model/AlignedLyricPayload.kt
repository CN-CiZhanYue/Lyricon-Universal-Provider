/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.model

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import kotlinx.serialization.Serializable

@Serializable
data class AlignedLyricPayload(
    val type: LyricTimingType,
    val displayLyric: String? = null,
    val rawLyric: String? = null,
    val translationLyric: String? = null,
    val lines: List<RichLyricLine> = emptyList()
)
