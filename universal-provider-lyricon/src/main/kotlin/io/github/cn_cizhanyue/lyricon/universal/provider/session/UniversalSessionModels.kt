/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.cn_cizhanyue.lyricon.universal.provider.session

data class RawSessionMetadata(
    val mediaId: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val displaySubtitle: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val playbackQueueItemId: Long? = null,
    val playbackExtrasMediaId: String? = null,
    val mediaUri: String? = null,
    val positionMs: Long? = null
)

data class TrackDescriptor(
    val userId: Int,
    val ownerPackage: String,
    val sessionInstanceId: String,
    val hostMediaId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val metadataRevision: Long,
    val trackGeneration: Long,
    val titlePolluted: Boolean,
    val rawTitle: String?,
    val playbackState: Int?,
    val playing: Boolean,
    val playbackQueueItemId: Long? = null,
    val playbackExtrasMediaId: String? = null,
    val mediaUri: String? = null,
    val positionMs: Long? = null
)

data class SessionObservation(
    val userId: Int,
    val ownerPackage: String,
    val sessionInstanceId: String,
    val raw: RawSessionMetadata,
    val playbackState: Int?,
    val playing: Boolean,
    val observedAtElapsedMs: Long
)

data class ResolvedSession(
    val observation: SessionObservation,
    val descriptor: TrackDescriptor,
    val demuxSource: String,
    val identityComplete: Boolean,
    /** [TrackIdentityResolver] decision for the latest payload; `standard` unless a torn payload was gated. */
    val identityGate: String = TrackIdentityResolver.GATE_STANDARD,
    /** When a held partial identity change may be accepted without another metadata callback. */
    val identitySettleAtElapsedMs: Long? = null
)

object PlaybackStates {
    const val NONE = 0
    const val STOPPED = 1
    const val PAUSED = 2
    const val PLAYING = 3
    const val FAST_FORWARDING = 4
    const val REWINDING = 5
    const val BUFFERING = 6
    const val ERROR = 7
    const val CONNECTING = 8
    const val SKIPPING_TO_PREVIOUS = 9
    const val SKIPPING_TO_NEXT = 10
    const val SKIPPING_TO_QUEUE_ITEM = 11

    fun isPlaying(state: Int?): Boolean = state == PLAYING
    fun isTransient(state: Int?): Boolean =
        state == BUFFERING || state == CONNECTING
}
