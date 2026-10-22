/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

internal object BluetoothLyricDemux {
    val RELAY_SEPARATORS = arrayOf(" - ", " – ", " — ")
    private val LRC_TIMESTAMP = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]")

    data class Result(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val hostMediaId: String?,
        val playbackQueueItemId: Long?,
        val playbackExtrasMediaId: String?,
        val mediaUri: String?,
        val positionMs: Long?,
        val titlePolluted: Boolean,
        val source: String,
        val rawTitle: String?
    )

    fun demux(raw: RawSessionMetadata): Result {
        val durationMs = normalizeDuration(raw.durationMs)
        val album = clean(raw.album)
        val hostMediaId = clean(raw.mediaId)
        val rawTitle = firstFilled(raw.title, raw.displayTitle)
        val displayTitle = clean(raw.displayTitle)
        val displaySubtitle = clean(raw.displaySubtitle)
        val albumArtist = clean(raw.albumArtist)
        val compositeArtist = clean(raw.artist)
        val surfaceLooksPolluted = looksLikeLyricTitle(rawTitle)

        if (displayTitle != null && displaySubtitle != null && !sameText(rawTitle, displayTitle)) {
            return Result(
                title = displayTitle,
                artist = displaySubtitle,
                album = album,
                durationMs = durationMs,
                hostMediaId = hostMediaId,
                playbackQueueItemId = raw.playbackQueueItemId,
                playbackExtrasMediaId = raw.playbackExtrasMediaId,
                mediaUri = raw.mediaUri,
                positionMs = raw.positionMs,
                titlePolluted = true,
                source = "display",
                rawTitle = rawTitle
            )
        }

        val subtracted = subtractAlbumArtist(compositeArtist, albumArtist)
        if (subtracted != null && !sameText(rawTitle, subtracted.title)) {
            return Result(
                title = subtracted.title,
                artist = subtracted.artist,
                album = album,
                durationMs = durationMs,
                hostMediaId = hostMediaId,
                playbackQueueItemId = raw.playbackQueueItemId,
                playbackExtrasMediaId = raw.playbackExtrasMediaId,
                mediaUri = raw.mediaUri,
                positionMs = raw.positionMs,
                titlePolluted = true,
                source = subtracted.source,
                rawTitle = rawTitle
            )
        }

        val split = splitCompositeArtist(compositeArtist)
        if (split != null && !sameText(rawTitle, split.title)) {
            return Result(
                title = split.title,
                artist = split.artist,
                album = album,
                durationMs = durationMs,
                hostMediaId = hostMediaId,
                playbackQueueItemId = raw.playbackQueueItemId,
                playbackExtrasMediaId = raw.playbackExtrasMediaId,
                mediaUri = raw.mediaUri,
                positionMs = raw.positionMs,
                titlePolluted = true,
                source = "relay-artist",
                rawTitle = rawTitle
            )
        }

        val title = firstFilled(rawTitle, displayTitle)
        val artist = firstFilled(albumArtist, compositeArtist, displaySubtitle)
        return Result(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            hostMediaId = hostMediaId,
            playbackQueueItemId = raw.playbackQueueItemId,
            playbackExtrasMediaId = raw.playbackExtrasMediaId,
            mediaUri = raw.mediaUri,
            positionMs = raw.positionMs,
            titlePolluted = surfaceLooksPolluted,
            source = if (surfaceLooksPolluted) "polluted-unresolved" else "standard",
            rawTitle = rawTitle
        )
    }

    fun looksLikeLyricTitle(title: String?): Boolean {
        val value = title ?: return false
        if (value.contains('\n') || value.contains('\r')) return true
        if (LRC_TIMESTAMP.containsMatchIn(value)) return true
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("[") && trimmed.contains("]")) return true
        val lower = trimmed.lowercase()
        if (lower.startsWith("作词") || lower.startsWith("作曲") || lower.startsWith("编曲") ||
            lower.startsWith("词：") || lower.startsWith("曲：") || lower.startsWith("composed by") ||
            lower.startsWith("written by") || lower.startsWith("produced by")) {
            return true
        }
        return false
    }

    fun normalizeDuration(durationMs: Long?): Long? {
        if (durationMs == null) return null
        if (durationMs <= 0L) return null
        if (durationMs > 24L * 60L * 60L * 1000L) return null
        return durationMs
    }

    fun sameDuration(left: Long?, right: Long?): Boolean {
        if (left == null || right == null) return left == null && right == null
        return kotlin.math.abs(left - right) < 1000L
    }

    internal fun subtractAlbumArtist(
        artist: String?,
        albumArtist: String?
    ): SplitIdentity? {
        val composite = artist?.trim().orEmpty()
        val cleanAlbumArtist = albumArtist?.trim().orEmpty()
        if (composite.isEmpty() || cleanAlbumArtist.isEmpty()) return null
        for (separator in RELAY_SEPARATORS) {
            val prefix = cleanAlbumArtist + separator
            if (composite.startsWith(prefix, ignoreCase = true)) {
                val title = composite.substring(prefix.length).trim()
                if (title.isNotEmpty() && !sameText(title, cleanAlbumArtist)) {
                    return SplitIdentity(title, cleanAlbumArtist, "album-artist-prefix")
                }
            }
            val suffix = separator + cleanAlbumArtist
            if (composite.endsWith(suffix, ignoreCase = true)) {
                val title = composite.substring(0, composite.length - suffix.length).trim()
                if (title.isNotEmpty() && !sameText(title, cleanAlbumArtist)) {
                    return SplitIdentity(title, cleanAlbumArtist, "album-artist-suffix")
                }
            }
        }
        return null
    }

    internal fun splitCompositeArtist(artist: String?): SplitIdentity? {
        val value = artist?.trim().orEmpty()
        var separatorIndex = -1
        var matched: String? = null
        for (separator in RELAY_SEPARATORS) {
            val index = value.indexOf(separator)
            if (index > 0 && (separatorIndex < 0 || index < separatorIndex)) {
                separatorIndex = index
                matched = separator
            }
        }
        if (separatorIndex < 0 || matched == null) return null
        val left = value.substring(0, separatorIndex).trim()
        val right = value.substring(separatorIndex + matched.length).trim()
        if (left.isEmpty() || right.isEmpty()) return null
        return SplitIdentity(title = right, artist = left, source = "relay-artist")
    }

    private fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun firstFilled(vararg values: String?): String? =
        values.firstNotNullOfOrNull { clean(it) }

    private fun sameText(left: String?, right: String?): Boolean =
        left?.trim()?.equals(right?.trim(), ignoreCase = true) == true

    data class SplitIdentity(
        val title: String,
        val artist: String,
        val source: String
    )
}
