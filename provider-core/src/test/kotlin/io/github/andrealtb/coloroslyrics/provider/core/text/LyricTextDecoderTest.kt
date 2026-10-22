/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.text

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricTextDecoderTest {
    @Test
    fun decodesUtf8BomUtf16AndGb18030() {
        val lyric = "[00:01.000]你好"
        assertEquals(
            lyric,
            LyricTextDecoder.decode(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + lyric.toByteArray()
            )
        )
        assertEquals(
            lyric,
            LyricTextDecoder.decode(
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                    lyric.toByteArray(Charsets.UTF_16LE)
            )
        )
        assertEquals(lyric, LyricTextDecoder.decode(lyric.toByteArray(Charset.forName("GB18030"))))
    }

    @Test
    fun rejectsInputBeyondBound() {
        assertNull(LyricTextDecoder.read(ByteArrayInputStream(ByteArray(9)), maxBytes = 8))
    }
}
