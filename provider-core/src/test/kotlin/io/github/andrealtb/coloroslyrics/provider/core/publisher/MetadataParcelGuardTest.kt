/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import org.junit.Test
import kotlin.test.assertEquals

class MetadataParcelGuardTest {
    @Test
    fun rejectsOversizedFieldBeforeParcelCommit() {
        assertEquals(
            MetadataParcelGuard.Result.FIELD_TOO_LARGE,
            MetadataParcelGuard.assessSizes(
                NativeLyricInfoPublisher.MAX_LYRIC_FIELD_CHARS + 1,
                1
            )
        )
    }

    @Test
    fun rejectsOversizedOrUnmeasurableParcel() {
        assertEquals(
            MetadataParcelGuard.Result.PARCEL_TOO_LARGE,
            MetadataParcelGuard.assessSizes(
                1,
                NativeLyricInfoPublisher.MAX_PARCEL_BYTES + 1
            )
        )
        assertEquals(
            MetadataParcelGuard.Result.MEASUREMENT_FAILED,
            MetadataParcelGuard.assessSizes(1, null)
        )
    }
}
