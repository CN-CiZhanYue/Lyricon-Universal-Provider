/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

class TrackIdentityResolver {
    companion object {
        const val GATE_STANDARD = "standard"
        const val GATE_STALE_TITLE = "stale-title"
        const val GATE_PARTIAL_PENDING = "partial-pending"
        const val GATE_PARTIAL_SETTLED = "partial-settled"

        /** How long a payload that keeps the title but changes its anchors may wait for a consistent one. */
        const val IDENTITY_SETTLE_MS = 2_000L

        /** A previous title paired with the current anchors is stale only this soon after a change. */
        const val STALE_TITLE_WINDOW_MS = 8_000L
        private const val RECENT_TITLE_LIMIT = 4
    }

    private data class SessionLock(
        var generation: Long = 0L,
        var metadataRevision: Long = 0L,
        var title: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var durationMs: Long? = null,
        var hostMediaId: String? = null,
        var playbackQueueItemId: Long? = null,
        var playbackExtrasMediaId: String? = null,
        var mediaUri: String? = null,
        var titlePolluted: Boolean = false,
        var demuxSource: String = "standard",
        var generationStartedAtElapsedMs: Long = 0L,
        val recentTitles: ArrayDeque<String> = ArrayDeque(),
        var pendingSignature: String? = null,
        var pendingSinceElapsedMs: Long = 0L,
        var staleSignature: String? = null
    )

    private val locks = LinkedHashMap<String, SessionLock>()

    @Synchronized
    fun observe(observation: SessionObservation): ResolvedSession {
        val demuxed = BluetoothLyricDemux.demux(observation.raw)
        val lock = locks.getOrPut(observation.sessionInstanceId) { SessionLock() }
        val now = observation.observedAtElapsedMs
        lock.metadataRevision += 1L

        val sameMediaId = sameFilled(lock.hostMediaId, demuxed.hostMediaId)
        val samePlaybackMediaId = sameFilled(lock.playbackExtrasMediaId, demuxed.playbackExtrasMediaId)
        val sameQueueId = lock.playbackQueueItemId != null && demuxed.playbackQueueItemId != null &&
            lock.playbackQueueItemId == demuxed.playbackQueueItemId
        val sameMediaUri = sameFilled(lock.mediaUri, demuxed.mediaUri)
        val hardIdentityChanged = (lock.hostMediaId != null && demuxed.hostMediaId != null && !sameMediaId) ||
            (lock.playbackExtrasMediaId != null && demuxed.playbackExtrasMediaId != null && !samePlaybackMediaId) ||
            (lock.mediaUri != null && demuxed.mediaUri != null && !sameMediaUri)
        val sameAnchor = sameAnchor(lock, demuxed)
        val sameResolved = sameText(lock.title, demuxed.title) && sameText(lock.artist, demuxed.artist)
        val baseSameTrack = when {
            lock.generation == 0L -> false
            hardIdentityChanged -> false
            sameMediaId && lock.hostMediaId != null && demuxed.hostMediaId != null -> true
            samePlaybackMediaId && lock.playbackExtrasMediaId != null && demuxed.playbackExtrasMediaId != null -> true
            sameQueueId -> true
            sameMediaUri && lock.mediaUri != null && demuxed.mediaUri != null -> true
            sameAnchor && (demuxed.titlePolluted || lock.titlePolluted) -> true
            // Bluetooth lyric metadata often restores the clean title before
            // album metadata catches up. During that polluted -> clean
            // transition, duration is the stable anchor; do not create a new
            // generation merely because album is temporarily missing.
            lock.titlePolluted && !demuxed.titlePolluted && sameDurationAnchor(lock, demuxed) -> true
            sameResolved && sameAnchor -> true
            sameResolved && (lock.durationMs == null || demuxed.durationMs == null) -> true
            else -> false
        }

        // Players without a hard identity may publish metadata that mixes two tracks: a title
        // captured at the track change with the artist/album/duration read later. Such a payload
        // must neither mint a generation of its own nor overwrite the current identity.
        var gate = GATE_STANDARD
        if (!baseSameTrack && lock.generation > 0L && !hardIdentityChanged &&
            !demuxed.titlePolluted && !lock.titlePolluted
        ) {
            val signature = signature(demuxed)
            // The stored metadata is re-read on every playback-state update, so a payload judged
            // stale stays stale after the arrival window instead of later becoming a new track.
            if (signature == lock.staleSignature || isStaleTitle(lock, demuxed, now)) {
                lock.staleSignature = signature
                gate = GATE_STALE_TITLE
            } else if (isPartialChange(lock, demuxed)) {
                if (lock.pendingSignature != signature) {
                    lock.pendingSignature = signature
                    lock.pendingSinceElapsedMs = now
                }
                // Accept an unresolved partial change once it has held, so a real same-title
                // track change still lands without waiting for the next metadata callback.
                gate = if (now - lock.pendingSinceElapsedMs < IDENTITY_SETTLE_MS) {
                    GATE_PARTIAL_PENDING
                } else {
                    GATE_PARTIAL_SETTLED
                }
            }
        }
        if (gate != GATE_PARTIAL_PENDING) lock.pendingSignature = null
        val sameTrack = baseSameTrack || gate == GATE_STALE_TITLE || gate == GATE_PARTIAL_PENDING

        if (!sameTrack) {
            lock.title?.trim()?.takeIf { it.isNotEmpty() }?.let { rememberTitle(lock, it) }
            lock.generation += 1L
            lock.generationStartedAtElapsedMs = now
            lock.staleSignature = null
            lock.title = if (demuxed.source == "polluted-unresolved") null else demuxed.title
            lock.artist = demuxed.artist
            lock.album = demuxed.album
            lock.durationMs = demuxed.durationMs
            lock.hostMediaId = demuxed.hostMediaId
            lock.playbackQueueItemId = demuxed.playbackQueueItemId
            lock.playbackExtrasMediaId = demuxed.playbackExtrasMediaId
            lock.mediaUri = demuxed.mediaUri
            lock.titlePolluted = demuxed.titlePolluted
            lock.demuxSource = demuxed.source
        } else if (gate == GATE_STANDARD) {
            if (!demuxed.titlePolluted && !demuxed.title.isNullOrBlank()) {
                lock.title = demuxed.title
            }
            lock.artist = firstFilled(lock.artist, demuxed.artist)
            lock.album = firstFilled(lock.album, demuxed.album)
            if (lock.durationMs == null) lock.durationMs = demuxed.durationMs
            if (lock.hostMediaId == null) lock.hostMediaId = demuxed.hostMediaId
            if (lock.playbackQueueItemId == null) lock.playbackQueueItemId = demuxed.playbackQueueItemId
            if (lock.playbackExtrasMediaId == null) lock.playbackExtrasMediaId = demuxed.playbackExtrasMediaId
            if (lock.mediaUri == null) lock.mediaUri = demuxed.mediaUri
            lock.titlePolluted = lock.titlePolluted || demuxed.titlePolluted
            if (demuxed.source != "polluted-unresolved") {
                lock.demuxSource = demuxed.source
            }
        }

        val descriptor = TrackDescriptor(
            userId = observation.userId,
            ownerPackage = observation.ownerPackage,
            sessionInstanceId = observation.sessionInstanceId,
            hostMediaId = lock.hostMediaId,
            title = lock.title,
            artist = lock.artist,
            album = lock.album,
            durationMs = lock.durationMs,
            metadataRevision = lock.metadataRevision,
            trackGeneration = lock.generation,
            titlePolluted = lock.titlePolluted,
            rawTitle = demuxed.rawTitle,
            playbackState = observation.playbackState,
            playing = observation.playing,
            playbackQueueItemId = lock.playbackQueueItemId,
            playbackExtrasMediaId = lock.playbackExtrasMediaId,
            mediaUri = lock.mediaUri,
            positionMs = demuxed.positionMs
        )
        return ResolvedSession(
            observation = observation,
            descriptor = descriptor,
            demuxSource = lock.demuxSource,
            identityComplete = !lock.title.isNullOrBlank() && !lock.artist.isNullOrBlank(),
            identityGate = gate,
            identitySettleAtElapsedMs = if (gate == GATE_PARTIAL_PENDING) {
                lock.pendingSinceElapsedMs + IDENTITY_SETTLE_MS
            } else null
        )
    }

    @Synchronized
    fun drop(sessionInstanceId: String) {
        locks.remove(sessionInstanceId)
    }

    @Synchronized
    fun reset() {
        locks.clear()
    }

    /** A title from a just-replaced track arriving with the current track's anchors (torn payload). */
    private fun isStaleTitle(lock: SessionLock, demuxed: BluetoothLyricDemux.Result, now: Long): Boolean {
        val incoming = demuxed.title?.takeIf { it.isNotBlank() } ?: return false
        if (lock.title.isNullOrBlank() || sameText(lock.title, incoming)) return false
        if (now - lock.generationStartedAtElapsedMs > STALE_TITLE_WINDOW_MS) return false
        if (lock.recentTitles.none { sameText(it, incoming) }) return false
        return sameFilled(lock.artist, demuxed.artist) &&
            lock.durationMs != null && demuxed.durationMs != null &&
            BluetoothLyricDemux.sameDuration(lock.durationMs, demuxed.durationMs) &&
            (lock.album.isNullOrBlank() || demuxed.album.isNullOrBlank() || sameText(lock.album, demuxed.album))
    }

    /** The current title arriving with another track's artist, album or duration. */
    private fun isPartialChange(lock: SessionLock, demuxed: BluetoothLyricDemux.Result): Boolean {
        if (lock.title.isNullOrBlank() || !sameText(lock.title, demuxed.title)) return false
        val artistChanged = !lock.artist.isNullOrBlank() && !demuxed.artist.isNullOrBlank() &&
            !sameText(lock.artist, demuxed.artist)
        val albumChanged = !lock.album.isNullOrBlank() && !demuxed.album.isNullOrBlank() &&
            !sameText(lock.album, demuxed.album)
        val durationChanged = lock.durationMs != null && demuxed.durationMs != null &&
            !BluetoothLyricDemux.sameDuration(lock.durationMs, demuxed.durationMs)
        return artistChanged || albumChanged || durationChanged
    }

    private fun signature(demuxed: BluetoothLyricDemux.Result): String = listOf(
        demuxed.title, demuxed.artist, demuxed.album, demuxed.durationMs?.toString()
    ).joinToString("|") { it?.trim()?.lowercase().orEmpty() }

    private fun rememberTitle(lock: SessionLock, title: String) {
        lock.recentTitles.removeAll { sameText(it, title) }
        lock.recentTitles.addFirst(title)
        while (lock.recentTitles.size > RECENT_TITLE_LIMIT) lock.recentTitles.removeLast()
    }

    private fun sameAnchor(lock: SessionLock, demuxed: BluetoothLyricDemux.Result): Boolean {
        val durationSame = sameDurationAnchor(lock, demuxed)
        val albumSame = lock.album.isNullOrBlank() ||
            demuxed.album.isNullOrBlank() ||
            sameText(lock.album, demuxed.album)
        return durationSame && albumSame
    }

    private fun sameDurationAnchor(lock: SessionLock, demuxed: BluetoothLyricDemux.Result): Boolean =
        BluetoothLyricDemux.sameDuration(lock.durationMs, demuxed.durationMs) ||
            lock.durationMs == null || demuxed.durationMs == null

    private fun sameFilled(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return sameText(left, right)
    }

    private fun firstFilled(previous: String?, incoming: String?): String? =
        previous?.trim()?.takeIf { it.isNotEmpty() } ?: incoming?.trim()?.takeIf { it.isNotEmpty() }

    private fun sameText(left: String?, right: String?): Boolean =
        left?.trim()?.equals(right?.trim(), ignoreCase = true) == true
}
