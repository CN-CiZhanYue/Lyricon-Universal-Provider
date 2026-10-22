/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.EnhanceLrcDocument
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine

object EnhanceLrcParser {

    private val TIME_PREFIX_REGEX = Regex("""^(\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?])+""")
    private val TAG_REGEX = Regex("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")
    private val WORD_REGEX = Regex("""<(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?>""")
    private val PERSON_REGEX = Regex("""^(v\d+|bg):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val META_REGEX = Regex("""^\[(\w+)\s*:\s*(.*)]$""")

    fun parse(raw: String?, duration: Long = 0): EnhanceLrcDocument {
        if (raw.isNullOrBlank()) return EnhanceLrcDocument(emptyMap(), emptyList())

        val lines = mutableListOf<RichLyricLine>()
        val meta = mutableMapOf<String, String>()
        val roles = mutableListOf<String>()

        raw.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim().trimStart('\uFEFF')
            if (!trimmed.startsWith("[")) return@forEach

            val timeMatch = TIME_PREFIX_REGEX.find(trimmed)
            if (timeMatch != null) {
                val content = trimmed.substring(timeMatch.range.last + 1).trim()
                val timeTags = timeMatch.value

                parseStandardLine(timeTags, content, roles).forEach { cur ->
                    mergeLines(lines, cur)
                }
            } else {
                handleMeta(trimmed, meta, lines, roles)
            }
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        val finalizedLines = finalize(lines, duration)

        return EnhanceLrcDocument(meta, finalizedLines).run {
            if (offset != 0L) applyOffset(offset) else this
        }
    }

    private fun mergeLines(lines: MutableList<RichLyricLine>, cur: RichLyricLine) {
        val last = lines.lastOrNull()
        if (last != null && last.begin == cur.begin) {
            last.secondary = cur.text
            last.secondaryWords = cur.words
            if (cur.isAlignedRight) last.isAlignedRight = true
        } else {
            lines.add(cur)
        }
    }

    private fun parseStandardLine(
        timeTags: String,
        content: String,
        roles: MutableList<String>
    ): List<RichLyricLine> {
        var person: String? = null
        var text = content

        PERSON_REGEX.matchEntire(content)?.let {
            person = it.groupValues[1].lowercase()
            text = it.groupValues[2]
            if (roles.isEmpty() && person != "bg") roles.add(person)
        }

        val lineTimeMatches = TAG_REGEX.findAll(timeTags).toList()
        val words = parseWords(text).ifEmpty {
            if (lineTimeMatches.size == 1) {
                val lineBegin = toMs(
                    lineTimeMatches[0].groupValues[1],
                    lineTimeMatches[0].groupValues[2],
                    lineTimeMatches[0].groupValues.getOrNull(3)
                )
                parseBracketInlineWords(text, lineBegin)
            } else {
                emptyList()
            }
        }
        val plainText = if (words.isNotEmpty()) {
            words.joinToString("") { it.text.orEmpty() }
        } else {
            text
        }

        val isRight =
            person == "bg" || (person != null && roles.isNotEmpty() && person != roles.first())

        val baseLineMs = lineTimeMatches.firstOrNull()?.let { first ->
            toMs(first.groupValues[1], first.groupValues[2], first.groupValues.getOrNull(3))
        } ?: 0L
        return lineTimeMatches.map { m ->
            val ms = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
            val shiftedWords = shiftWords(words, ms - baseLineMs)
            RichLyricLine(
                begin = ms,
                end = shiftedWords.lastOrNull()?.end?.takeIf { it > ms } ?: ms,
                text = plainText,
                words = shiftedWords.takeIf { it.isNotEmpty() },
                isAlignedRight = isRight
            )
        }.toList()
    }

    private fun shiftWords(words: List<LyricWord>, deltaMs: Long): List<LyricWord> =
        words.map { word ->
            val begin = (word.begin + deltaMs).coerceAtLeast(0L)
            val end = if (word.end > word.begin) {
                (word.end + deltaMs).coerceAtLeast(begin)
            } else {
                begin
            }
            word.copy(begin = begin, end = end, duration = end - begin)
        }

    /**
     * Parses the enhanced-LRC variant used by Salt/TME where the line tag is also the first
     * word's start and later words use bracketed timestamps:
     * `[00:11.367]I [00:11.548]heard ... [00:14.903]`.
     *
     * A visible segment must precede the first inline timestamp. This keeps ordinary repeated
     * line tags (`[00:10][00:20]text`) and literal bracketed text on the standard LRC path. A
     * final timestamp with no following text closes the previous word instead of creating an
     * empty word.
     */
    private fun parseBracketInlineWords(content: String, lineBegin: Long): List<LyricWord> {
        val matches = TAG_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()

        val leadingText = content.substring(0, matches.first().range.first)
        if (leadingText.isBlank()) return emptyList()

        val words = mutableListOf(LyricWord(begin = lineBegin, text = leadingText))
        matches.forEachIndexed { index, match ->
            val wordBegin = toMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues.getOrNull(3)
            )
            words.lastOrNull()?.let { previous ->
                previous.end = wordBegin.coerceAtLeast(previous.begin)
                previous.duration = (previous.end - previous.begin).coerceAtLeast(0L)
            }

            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val segment = content.substring(match.range.last + 1, nextStart)
            if (segment.isNotEmpty()) {
                words += LyricWord(begin = wordBegin, text = segment)
            }
        }
        return words
    }

    private fun parseWords(content: String): List<LyricWord> {
        val matches = WORD_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { i, m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
            val nextMatch = matches.getOrNull(i + 1)
            val text =
                content.substring(m.range.last + 1, nextMatch?.range?.first ?: content.length)

            LyricWord(begin = start, text = text).apply {
                nextMatch?.let { next ->
                    end = toMs(
                        next.groupValues[1],
                        next.groupValues[2],
                        next.groupValues.getOrNull(3)
                    )
                    duration = (end - begin).coerceAtLeast(0)
                }
            }
        }.filter { !it.text.isNullOrEmpty() }
    }

    private fun toMs(mStr: String, sStr: String, fStr: String?): Long {
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        val ms = when (fStr?.length) {
            1 -> fStr.toLong() * 100
            2 -> fStr.toLong() * 10
            3 -> fStr.toLong()
            else -> 0L
        }
        return m * 60000 + s * 1000 + ms
    }

    private fun handleMeta(
        line: String,
        meta: MutableMap<String, String>,
        lines: MutableList<RichLyricLine>,
        roles: List<String>
    ) {
        META_REGEX.matchEntire(line)?.let { m ->
            val tag = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()

            if (tag == "bg" && lines.isNotEmpty()) {
                val words = parseWords(value)
                lines.last().apply {
                    secondary =
                        if (words.isNotEmpty()) words.joinToString("") { it.text ?: "" } else value
                    secondaryWords = words.takeIf { it.isNotEmpty() }
                }
            } else {
                meta[tag] = value
            }
        }
    }

    private fun finalize(lines: List<RichLyricLine>, totalDur: Long): List<RichLyricLine> {
        val sorted = lines.sortedBy { it.begin }
        sorted.forEachIndexed { i, cur ->
            if (cur.end <= cur.begin) {
                val nextBegin = sorted.getOrNull(i + 1)?.begin
                cur.end = nextBegin ?: if (totalDur > cur.begin) totalDur else cur.begin + 5000L
            }
            cur.duration = (cur.end - cur.begin).coerceAtLeast(0)
        }
        return sorted
    }
}
