/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.LrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import java.util.Locale

object ColorOSLyricJsonEncoder {

    const val METADATA_KEY_LYRIC_INFO = "lyricInfo"

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val TIMED_LRC_REGEX = Regex("""[\[<][0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?[\]>]""")

    data class EncodedPayload(
        val jsonValue: String,
        val plainLyric: String,
        val rawLyric: String,
        val translationLyric: String
    )

    fun encode(
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        trackGeneration: Long,
        playerPackage: String
    ): EncodedPayload? {
        val validLines = lines.filter { !it.text.isNullOrBlank() }.sortedBy { it.begin }
        if (validLines.isEmpty()) return null

        val plainLyric = toPlainLrc(track, validLines)
        if (plainLyric.isBlank()) return null
        val rawLyric = toEnhancedLrc(track, validLines)
        val translationLyric = toTranslationLrc(track, validLines)

        val fields = mutableListOf(
            jsonStringField("songName", track.title.orEmpty()),
            jsonStringField("artist", track.artist.orEmpty()),
            jsonStringField("songId", track.id.orEmpty()),
            "\"lyricType\":0",
            jsonStringField("id", ""),
            jsonStringField("lyric", plainLyric),
            "\"noLyric\":false",
            jsonStringField("provider", playerPackage),
            jsonStringField("source", "$playerPackage-v5"),
            jsonStringField("trackKey", track.buildStableKey()),
            "\"sessionGeneration\":$trackGeneration"
        )

        if (containsTimedLrc(rawLyric)) fields += jsonStringField("rawLyric", rawLyric)
        if (containsTimedLrc(translationLyric)) fields += jsonStringField("translationLyric", translationLyric)

        return EncodedPayload(
            jsonValue = fields.joinToString(prefix = "{", postfix = "}"),
            plainLyric = plainLyric,
            rawLyric = rawLyric,
            translationLyric = translationLyric
        )
    }

    private fun toEnhancedLrc(track: TrackIdentity, lines: List<RichLyricLine>): String {
        val builder = StringBuilder()
        appendMetadata(builder, track)
        lines.forEach { line ->
            val words = line.words.orEmpty().filter { !it.text.isNullOrEmpty() }
            if (words.isEmpty()) {
                appendTimedLine(builder, line.begin, line.text.orEmpty())
                return@forEach
            }
            builder.append('[').append(formatLrcTime(line.begin)).append(']')
            var previousTime = line.begin
            var lastWordEnd = line.begin
            words.forEachIndexed { index, word ->
                var wordTime = word.begin.coerceAtLeast(line.begin)
                if (wordTime < previousTime) {
                    val span = (line.duration.coerceAtLeast(line.end - line.begin))
                        .coerceIn(360L, 12_000L)
                    wordTime = line.begin + (span / words.size).coerceAtLeast(80L) * index
                }
                builder.append('<')
                    .append(formatLrcTime(wordTime))
                    .append('>')
                    .append(cleanInlineSegment(word.text.orEmpty()))
                previousTime = wordTime
                lastWordEnd = word.end.coerceAtLeast(wordTime)
            }
            val lineEnd = maxOf(line.end, lastWordEnd)
            if (lineEnd > line.begin) {
                builder.append('<').append(formatLrcTime(lineEnd)).append('>')
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun toPlainLrc(track: TrackIdentity, lines: List<RichLyricLine>): String {
        val builder = StringBuilder()
        appendMetadata(builder, track)
        lines.forEach { line -> appendTimedLine(builder, line.begin, line.text.orEmpty()) }
        return builder.toString()
    }

    private fun toTranslationLrc(track: TrackIdentity, lines: List<RichLyricLine>): String {
        val builder = StringBuilder()
        appendMetadata(builder, track)
        lines.forEach { line ->
            val translation = line.secondary
            if (!translation.isNullOrBlank() && isUsableTranslation(translation, line.text)) {
                appendTimedLine(builder, line.begin, translation)
            }
        }
        return builder.toString()
    }

    private fun appendMetadata(builder: StringBuilder, track: TrackIdentity) {
        val title = cleanPlainText(track.title.orEmpty())
        val artist = cleanPlainText(track.artist.orEmpty())
        if (title.isNotBlank()) builder.append("[ti:").append(title).append("]\n")
        if (artist.isNotBlank()) builder.append("[ar:").append(artist).append("]\n")
    }

    private fun appendTimedLine(builder: StringBuilder, timeMillis: Long, text: String) {
        val clean = cleanPlainText(text)
        if (clean.isBlank()) return
        builder.append('[').append(formatLrcTime(timeMillis)).append(']')
            .append(clean).append('\n')
    }

    private fun isUsableTranslation(translation: String, primary: String?): Boolean {
        val clean = cleanPlainText(translation)
        return clean.isNotBlank() && clean != "//" && clean != cleanPlainText(primary.orEmpty())
    }

    private fun cleanInlineSegment(text: String): String =
        text.replace('\r', ' ').replace('\n', ' ')

    private fun cleanPlainText(text: String): String =
        text.replace('\r', ' ').replace('\n', ' ').replace(WHITESPACE_REGEX, " ").trim()

    private fun containsTimedLrc(value: String): Boolean = TIMED_LRC_REGEX.containsMatchIn(value)

    private fun formatLrcTime(ms: Long): String {
        val min = ms / 60000
        val sec = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(Locale.ROOT, "%02d:%02d.%03d", min, sec, millis)
    }

    private fun jsonStringField(name: String, value: String): String =
        "\"" + escapeJson(name) + "\":\"" + escapeJson(value) + "\""

    private fun escapeJson(value: String): String = buildString(value.length + 16) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
