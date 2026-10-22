/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import android.media.MediaMetadata
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackGenerationPolicy
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine

object NativeLyricInfoPublisher {

    const val MAX_PARCEL_BYTES = 512 * 1024
    const val MAX_LYRIC_FIELD_CHARS = 1_500_000

    enum class Result {
        PUBLISHED,
        INVALID_INPUT,
        HOST_PACKAGE_MISMATCH,
        STALE_GENERATION,
        ENCODE_FAILED,
        PAYLOAD_TOO_LARGE,
        PARCEL_MEASUREMENT_FAILED,
        COMMIT_FAILED,

        /** The host metadata bundle is not reachable, so the append-only write was skipped. */
        APPEND_UNSUPPORTED;

        val isPublished: Boolean
            get() = this == PUBLISHED
    }

    /**
     * Adds lyricInfo only after a complete candidate metadata object has passed all gates.
     * The supplied builder is therefore never mutated on a rejected publication.
     * Callers must seed [builder] and [originalMetadata] from the same host metadata snapshot.
     */
    fun publishToPlatformMetadata(
        builder: MediaMetadata.Builder,
        originalMetadata: MediaMetadata?,
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        generationPolicy: TrackGenerationPolicy,
        playerPackage: String,
        hostPackage: String
    ): Result = publishTransactional(
        builder = builder,
        originalMetadata = originalMetadata,
        track = track,
        lines = lines,
        trackGeneration = trackGeneration,
        generationPolicy = generationPolicy,
        playerPackage = playerPackage,
        hostPackage = hostPackage,
        transaction = AndroidMetadataTransaction
    )

    /**
     * Append-only publication. Runs the same gates as [publishToPlatformMetadata], then writes the
     * single lyricInfo key into [metadata] itself through [HostMetadataOverlay] instead of building
     * a copy, so every artwork lane and unknown key reaches SystemUI exactly as the host published
     * it. Any rejection leaves [metadata] as the caller passed it.
     */
    fun publishToHostMetadata(
        metadata: MediaMetadata,
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        generationPolicy: TrackGenerationPolicy,
        playerPackage: String,
        hostPackage: String
    ): Result = publishInPlace(
        track = track,
        lines = lines,
        trackGeneration = trackGeneration,
        generationPolicy = generationPolicy,
        playerPackage = playerPackage,
        hostPackage = hostPackage
    ) { lyricInfo -> HostMetadataOverlay.putLyricInfo(metadata, lyricInfo) }

    internal fun <M, B> publishTransactional(
        builder: B,
        originalMetadata: M?,
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        generationPolicy: TrackGenerationPolicy,
        playerPackage: String,
        hostPackage: String,
        transaction: MetadataTransaction<M, B>
    ): Result {
        val encoded = when (
            val gate = encodeIfCurrent(
                hasMetadata = originalMetadata != null,
                track = track,
                lines = lines,
                trackGeneration = trackGeneration,
                generationPolicy = generationPolicy,
                playerPackage = playerPackage,
                hostPackage = hostPackage
            )
        ) {
            is Gate.Rejected -> return gate.result
            is Gate.Encoded -> gate.payload
        }

        val candidate = runCatching {
            transaction.buildCandidate(
                originalMetadata,
                ColorOSLyricJsonEncoder.METADATA_KEY_LYRIC_INFO,
                encoded.jsonValue
            )
        }.getOrNull() ?: return Result.PARCEL_MEASUREMENT_FAILED

        val parcelBytes = runCatching { transaction.measureParcelBytes(candidate) }.getOrNull()
            ?: return Result.PARCEL_MEASUREMENT_FAILED
        if (parcelBytes > MAX_PARCEL_BYTES) return Result.PAYLOAD_TOO_LARGE

        val committed = runCatching {
            transaction.commit(
                builder,
                ColorOSLyricJsonEncoder.METADATA_KEY_LYRIC_INFO,
                encoded.jsonValue
            )
        }.isSuccess
        if (!committed) return Result.COMMIT_FAILED
        logPublished(track, trackGeneration, playerPackage, encoded, parcelBytes)
        return Result.PUBLISHED
    }

    internal fun publishInPlace(
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        generationPolicy: TrackGenerationPolicy,
        playerPackage: String,
        hostPackage: String,
        write: (lyricInfo: String) -> HostMetadataOverlay.Outcome
    ): Result {
        val encoded = when (
            val gate = encodeIfCurrent(
                hasMetadata = true,
                track = track,
                lines = lines,
                trackGeneration = trackGeneration,
                generationPolicy = generationPolicy,
                playerPackage = playerPackage,
                hostPackage = hostPackage
            )
        ) {
            is Gate.Rejected -> return gate.result
            is Gate.Encoded -> gate.payload
        }
        val outcome = runCatching { write(encoded.jsonValue) }.getOrNull()
            ?: return Result.COMMIT_FAILED
        return when (outcome.result) {
            HostMetadataOverlay.Result.WRITTEN -> {
                logPublished(track, trackGeneration, playerPackage, encoded, outcome.parcelBytes)
                Result.PUBLISHED
            }

            HostMetadataOverlay.Result.UNCHANGED -> Result.PUBLISHED
            HostMetadataOverlay.Result.UNSUPPORTED -> Result.APPEND_UNSUPPORTED
            HostMetadataOverlay.Result.FIELD_TOO_LARGE,
            HostMetadataOverlay.Result.PARCEL_TOO_LARGE -> Result.PAYLOAD_TOO_LARGE

            HostMetadataOverlay.Result.MEASUREMENT_FAILED -> Result.PARCEL_MEASUREMENT_FAILED
        }
    }

    private sealed interface Gate {
        data class Rejected(val result: Result) : Gate
        data class Encoded(val payload: ColorOSLyricJsonEncoder.EncodedPayload) : Gate
    }

    private fun encodeIfCurrent(
        hasMetadata: Boolean,
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        generationPolicy: TrackGenerationPolicy,
        playerPackage: String,
        hostPackage: String
    ): Gate {
        if (!hasMetadata || track.isBlank || lines.isEmpty()) return Gate.Rejected(Result.INVALID_INPUT)
        if (playerPackage.isBlank() || playerPackage != hostPackage) {
            return Gate.Rejected(Result.HOST_PACKAGE_MISMATCH)
        }
        if (!generationPolicy.isGenerationValid(trackGeneration) ||
            !TrackIdentityPolicy.isSameTrack(generationPolicy.currentTrack, track)
        ) {
            return Gate.Rejected(Result.STALE_GENERATION)
        }

        val encoded = runCatching {
            ColorOSLyricJsonEncoder.encode(track, lines, trackGeneration, playerPackage)
        }.getOrNull() ?: return Gate.Rejected(Result.ENCODE_FAILED)
        if (encoded.jsonValue.length > MAX_LYRIC_FIELD_CHARS) {
            return Gate.Rejected(Result.PAYLOAD_TOO_LARGE)
        }
        return Gate.Encoded(encoded)
    }

    private fun logPublished(
        track: TrackIdentity,
        trackGeneration: Long,
        playerPackage: String,
        encoded: ColorOSLyricJsonEncoder.EncodedPayload,
        parcelBytes: Int?
    ) {
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = "provider/${playerPackage.substringAfterLast('.')}",
                area = "publisher",
                event = "LYRIC_INFO_PUBLISHED",
                generation = trackGeneration,
                trackHash = DiagnosticHasher.sha256(track.buildStableKey()),
                payloadChars = encoded.jsonValue.length,
                parcelBytes = parcelBytes
            )
        )
    }

    internal interface MetadataTransaction<M, B> {
        fun buildCandidate(originalMetadata: M?, key: String, value: String): M
        fun measureParcelBytes(metadata: M): Int?
        fun commit(builder: B, key: String, value: String)
    }

    private object AndroidMetadataTransaction : MetadataTransaction<MediaMetadata, MediaMetadata.Builder> {
        override fun buildCandidate(originalMetadata: MediaMetadata?, key: String, value: String): MediaMetadata {
            val candidateBuilder = originalMetadata?.let(MediaMetadata::Builder) ?: MediaMetadata.Builder()
            return candidateBuilder.putString(key, value).build()
        }

        override fun measureParcelBytes(metadata: MediaMetadata): Int? =
            MetadataParcelGuard.measureParcelBytes(metadata)

        override fun commit(builder: MediaMetadata.Builder, key: String, value: String) {
            builder.putString(key, value)
        }
    }
}
