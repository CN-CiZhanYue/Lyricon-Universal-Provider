/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import android.media.MediaMetadata
import android.os.Parcel

/** Binder-size fail-open guard shared by lyricInfo metadata publishers. */
object MetadataParcelGuard {
    enum class Result {
        SAFE,
        FIELD_TOO_LARGE,
        PARCEL_TOO_LARGE,
        MEASUREMENT_FAILED
    }

    internal fun assessSizes(fieldChars: Int, parcelBytes: Int?): Result {
        if (fieldChars > NativeLyricInfoPublisher.MAX_LYRIC_FIELD_CHARS) {
            return Result.FIELD_TOO_LARGE
        }
        if (parcelBytes == null || parcelBytes < 0) return Result.MEASUREMENT_FAILED
        if (parcelBytes > NativeLyricInfoPublisher.MAX_PARCEL_BYTES) {
            return Result.PARCEL_TOO_LARGE
        }
        return Result.SAFE
    }

    internal fun measureParcelBytes(metadata: MediaMetadata): Int? {
        var parcel: Parcel? = null
        return try {
            parcel = Parcel.obtain()
            metadata.writeToParcel(parcel, 0)
            parcel.dataSize()
        } catch (_: Throwable) {
            null
        } finally {
            parcel?.recycle()
        }
    }
}
