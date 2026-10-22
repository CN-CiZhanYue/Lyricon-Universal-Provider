/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc.model

import kotlinx.serialization.Serializable

@Serializable
data class RichLyricLine(
    var begin: Long = 0L,
    var end: Long = 0L,
    var duration: Long = (end - begin).coerceAtLeast(0L),
    var text: String? = null,
    var words: List<LyricWord>? = null,
    var isAlignedRight: Boolean = false,
    var secondary: String? = null,
    var secondaryWords: List<LyricWord>? = null
)
