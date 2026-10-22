/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.text

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

/** Bounded BOM/UTF-8/GB18030 decoder for local lyric files. */
object LyricTextDecoder {
    fun read(input: InputStream, maxBytes: Int): String? {
        if (maxBytes <= 0) return null
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_BYTES))
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val decoded = when {
            bytes.startsWith(UTF8_BOM) -> String(
                bytes,
                UTF8_BOM.size,
                bytes.size - UTF8_BOM.size,
                StandardCharsets.UTF_8
            )
            bytes.startsWith(UTF16_LE_BOM) -> String(
                bytes,
                UTF16_LE_BOM.size,
                bytes.size - UTF16_LE_BOM.size,
                StandardCharsets.UTF_16LE
            )
            bytes.startsWith(UTF16_BE_BOM) -> String(
                bytes,
                UTF16_BE_BOM.size,
                bytes.size - UTF16_BE_BOM.size,
                StandardCharsets.UTF_16BE
            )
            else -> decodeStrictUtf8(bytes) ?: String(bytes, Charset.forName("GB18030"))
        }
        return decoded.trimStart('\uFEFF').takeIf { it.isNotBlank() }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private const val DEFAULT_BUFFER_BYTES = 8 * 1024
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
