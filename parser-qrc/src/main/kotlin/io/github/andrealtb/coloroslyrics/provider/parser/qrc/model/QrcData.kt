/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.qrc.model

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricLine
import kotlinx.serialization.Serializable

@Serializable
data class QrcData(
    val metaData: Map<String, String>,
    val lines: List<LyricLine>
)
