/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.mode

import kotlinx.serialization.Serializable

@Serializable
data class RuntimeModeResolution(
    val mode: RuntimeMode,
    val hostPackage: String,
    val processName: String,
    val markerSource: String
)
