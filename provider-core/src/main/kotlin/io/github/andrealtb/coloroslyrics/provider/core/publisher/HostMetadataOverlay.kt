/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import android.media.MediaMetadata
import java.lang.ref.WeakReference

/**
 * Append-only writes into a [MediaMetadata] the host already built.
 *
 * ColorOS derives the lockscreen cover from the bitmap lanes of exactly the metadata a session
 * publishes (com.oplus.systemui.media.controls.pipeline.OplusMediaDataManagerExImpl tries
 * loadBitmapFromUri, then METADATA_KEY_ART, then METADATA_KEY_ALBUM_ART). Re-creating metadata
 * through MediaMetadata.Builder, from an existing instance or key by key, hands every bitmap to
 * framework copy semantics, and on ColorOS that boundary is not lossless (a 512x512 cover arrived
 * as a 1x1 solid bitmap). A Provider therefore never rebuilds metadata: it writes its own keys into
 * the value bundle the host metadata already carries and leaves every artwork lane, URI, rating
 * and unknown key, including the Bitmap instances themselves, exactly as the host published them.
 *
 * lyricInfo writes are parcel-guarded and rolled back when the result would not fit a Binder
 * transaction. The host's own lyricInfo is remembered per metadata object, so encoders that patch
 * the official payload keep reading the host value after the module wrote into that object.
 * When the bundle cannot be reached every call fails open with [Result.UNSUPPORTED] and leaves the
 * metadata untouched.
 */
object HostMetadataOverlay {
    const val KEY_LYRIC_INFO = "lyricInfo"

    enum class Result {
        /** The value is now in the host metadata. */
        WRITTEN,

        /** The host metadata already carried exactly this value; nothing was written. */
        UNCHANGED,

        /** The host metadata bundle is not reachable; nothing was written. */
        UNSUPPORTED,
        FIELD_TOO_LARGE,
        PARCEL_TOO_LARGE,
        MEASUREMENT_FAILED;

        val isApplied: Boolean
            get() = this == WRITTEN || this == UNCHANGED
    }

    data class Outcome(val result: Result, val parcelBytes: Int? = null)

    private val engine = MetadataOverlayEngine(AndroidMetadataStore)

    /** The lyricInfo the host itself put on [metadata], ignoring module writes into that object. */
    fun hostLyricInfo(metadata: MediaMetadata): String? = engine.hostValue(metadata, KEY_LYRIC_INFO)

    fun putLyricInfo(metadata: MediaMetadata, lyricInfo: String): Outcome =
        engine.putGuarded(metadata, KEY_LYRIC_INFO, lyricInfo)

    /** Overwrites a non-empty lyricInfo with "" so ColorOS drops a stale payload. */
    fun clearLyricInfo(metadata: MediaMetadata): Result = engine.clear(metadata, KEY_LYRIC_INFO)

    /** Writes one text key, the same way MediaMetadata.Builder.putString would. */
    fun putText(metadata: MediaMetadata, key: String, value: String): Result =
        engine.putText(metadata, key, value)

    private object AndroidMetadataStore : MetadataOverlayEngine.Store<MediaMetadata> {
        override fun isWritable(metadata: MediaMetadata): Boolean =
            HostMetadataBundle.bundleOf(metadata) != null

        override fun contains(metadata: MediaMetadata, key: String): Boolean =
            metadata.containsKey(key)

        override fun read(metadata: MediaMetadata, key: String): String? = metadata.getString(key)

        override fun write(metadata: MediaMetadata, key: String, value: String) {
            // MediaMetadata.Builder.putString stores text through putCharSequence.
            checkNotNull(HostMetadataBundle.bundleOf(metadata)).putCharSequence(key, value)
        }

        override fun remove(metadata: MediaMetadata, key: String) {
            HostMetadataBundle.bundleOf(metadata)?.remove(key)
        }

        override fun measureParcelBytes(metadata: MediaMetadata): Int? =
            MetadataParcelGuard.measureParcelBytes(metadata)
    }
}

/** Platform-free core of [HostMetadataOverlay], kept generic so the write rules are unit-testable. */
internal class MetadataOverlayEngine<M : Any>(
    private val store: Store<M>,
    private val trackedObjects: Int = TRACKED_OBJECTS
) {
    interface Store<M> {
        fun isWritable(metadata: M): Boolean
        fun contains(metadata: M, key: String): Boolean
        fun read(metadata: M, key: String): String?
        fun write(metadata: M, key: String, value: String)
        fun remove(metadata: M, key: String)
        fun measureParcelBytes(metadata: M): Int?
    }

    private class Original<M : Any>(
        val metadata: WeakReference<M>,
        val key: String,
        val value: String?
    )

    private val lock = Any()
    private val originals = ArrayDeque<Original<M>>()

    fun hostValue(metadata: M, key: String): String? = synchronized(lock) {
        val original = findOriginal(metadata, key)
        if (original != null) original.value else runCatching { store.read(metadata, key) }.getOrNull()
    }

    fun putGuarded(metadata: M, key: String, value: String): HostMetadataOverlay.Outcome {
        if (value.length > NativeLyricInfoPublisher.MAX_LYRIC_FIELD_CHARS) {
            return HostMetadataOverlay.Outcome(HostMetadataOverlay.Result.FIELD_TOO_LARGE)
        }
        synchronized(lock) {
            if (!isWritable(metadata)) {
                return HostMetadataOverlay.Outcome(HostMetadataOverlay.Result.UNSUPPORTED)
            }
            val present = contains(metadata, key)
            val previous = read(metadata, key)
            if (present && previous == value) {
                return HostMetadataOverlay.Outcome(HostMetadataOverlay.Result.UNCHANGED)
            }
            rememberOriginal(metadata, key, previous)
            if (!write(metadata, key, value)) {
                restore(metadata, key, present, previous)
                return HostMetadataOverlay.Outcome(HostMetadataOverlay.Result.UNSUPPORTED)
            }
            val parcelBytes = runCatching { store.measureParcelBytes(metadata) }.getOrNull()
            val rejected = when (MetadataParcelGuard.assessSizes(value.length, parcelBytes)) {
                MetadataParcelGuard.Result.SAFE ->
                    return HostMetadataOverlay.Outcome(HostMetadataOverlay.Result.WRITTEN, parcelBytes)

                MetadataParcelGuard.Result.FIELD_TOO_LARGE -> HostMetadataOverlay.Result.FIELD_TOO_LARGE
                MetadataParcelGuard.Result.PARCEL_TOO_LARGE -> HostMetadataOverlay.Result.PARCEL_TOO_LARGE
                MetadataParcelGuard.Result.MEASUREMENT_FAILED -> HostMetadataOverlay.Result.MEASUREMENT_FAILED
            }
            restore(metadata, key, present, previous)
            return HostMetadataOverlay.Outcome(rejected, parcelBytes)
        }
    }

    fun clear(metadata: M, key: String): HostMetadataOverlay.Result = synchronized(lock) {
        if (!isWritable(metadata)) return HostMetadataOverlay.Result.UNSUPPORTED
        val present = contains(metadata, key)
        val previous = read(metadata, key)
        if (previous.isNullOrEmpty()) return HostMetadataOverlay.Result.UNCHANGED
        rememberOriginal(metadata, key, previous)
        if (!write(metadata, key, "")) {
            restore(metadata, key, present, previous)
            return HostMetadataOverlay.Result.UNSUPPORTED
        }
        HostMetadataOverlay.Result.WRITTEN
    }

    fun putText(metadata: M, key: String, value: String): HostMetadataOverlay.Result = synchronized(lock) {
        if (!isWritable(metadata)) return HostMetadataOverlay.Result.UNSUPPORTED
        val present = contains(metadata, key)
        val previous = read(metadata, key)
        if (present && previous == value) return HostMetadataOverlay.Result.UNCHANGED
        if (!write(metadata, key, value)) {
            restore(metadata, key, present, previous)
            return HostMetadataOverlay.Result.UNSUPPORTED
        }
        HostMetadataOverlay.Result.WRITTEN
    }

    private fun isWritable(metadata: M): Boolean =
        runCatching { store.isWritable(metadata) }.getOrDefault(false)

    private fun contains(metadata: M, key: String): Boolean =
        runCatching { store.contains(metadata, key) }.getOrDefault(false)

    private fun read(metadata: M, key: String): String? =
        runCatching { store.read(metadata, key) }.getOrNull()

    private fun write(metadata: M, key: String, value: String): Boolean =
        runCatching { store.write(metadata, key, value) }.isSuccess

    private fun restore(metadata: M, key: String, present: Boolean, previous: String?) {
        runCatching {
            if (present && previous != null) {
                store.write(metadata, key, previous)
            } else {
                store.remove(metadata, key)
            }
        }
    }

    private fun findOriginal(metadata: M, key: String): Original<M>? =
        originals.firstOrNull { it.key == key && it.metadata.get() === metadata }

    private fun rememberOriginal(metadata: M, key: String, value: String?) {
        if (findOriginal(metadata, key) != null) return
        originals.removeAll { it.metadata.get() == null }
        while (originals.size >= trackedObjects) {
            originals.removeFirst()
        }
        originals.addLast(Original(WeakReference(metadata), key, value))
    }

    private companion object {
        /** Hosts re-send only their latest few metadata objects; older originals are never read. */
        const val TRACKED_OBJECTS = 8
    }
}
