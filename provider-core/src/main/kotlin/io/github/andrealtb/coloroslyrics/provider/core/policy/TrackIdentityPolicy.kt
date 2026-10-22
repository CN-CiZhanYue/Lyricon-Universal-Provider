/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import kotlin.math.abs

object TrackIdentityPolicy {

    fun isSameTrack(previous: TrackIdentity?, current: TrackIdentity?): Boolean {
        if (previous == null || current == null) return false
        if (previous.isBlank || current.isBlank) return false

        if (!previous.id.isNullOrBlank() && !current.id.isNullOrBlank()) {
            return previous.id.trim() == current.id.trim()
        }

        val titleMatch = normalizeString(previous.title) == normalizeString(current.title)
        val artistMatch = normalizeString(previous.artist) == normalizeString(current.artist)

        if (titleMatch && artistMatch) {
            if (previous.durationMs > 0 && current.durationMs > 0) {
                return abs(previous.durationMs - current.durationMs) <= 3000L
            }
            return true
        }

        return false
    }

    fun hasTrackChanged(previous: TrackIdentity?, current: TrackIdentity?): Boolean =
        !isSameTrack(previous, current)

    fun mergePreferringFilled(previous: TrackIdentity, incoming: TrackIdentity): TrackIdentity =
        TrackIdentity(
            id = firstFilled(previous.id, incoming.id),
            title = firstFilled(previous.title, incoming.title),
            artist = firstFilled(previous.artist, incoming.artist),
            album = firstFilled(previous.album, incoming.album),
            durationMs = if (previous.durationMs > 0L) previous.durationMs else incoming.durationMs
        )

    private fun firstFilled(previous: String?, incoming: String?): String? =
        previous?.trim()?.takeIf { it.isNotEmpty() } ?: incoming?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeString(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim()
            .lowercase()
            .replace(Regex("""\s+"""), " ")
    }
}
