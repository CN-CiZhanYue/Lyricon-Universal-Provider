/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackIdentity(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L
) {
    val isBlank: Boolean
        get() = id.isNullOrBlank() && title.isNullOrBlank() && artist.isNullOrBlank()

    fun buildStableKey(): String {
        val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: "noid"
        val cleanTitle = title?.trim()?.lowercase() ?: "notitle"
        val cleanArtist = artist?.trim()?.lowercase() ?: "noartist"
        val durBucket = if (durationMs > 0) durationMs / 1000 else 0
        return "$cleanId|$cleanTitle|$cleanArtist|$durBucket"
    }
}
