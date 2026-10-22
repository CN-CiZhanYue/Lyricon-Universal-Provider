/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LrcDocument
import java.util.regex.Pattern

/**
 * Standard LRC Parser.
 * Handles non-standard formats, bracket preservation in lyric content, multi-tags, and custom offsets.
 */
object LrcParser {

    private val LINE_VALIDATOR =
        Pattern.compile("""^\[\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?].*""")

    private val TAG_PATTERN =
        Pattern.compile("""\[(\d{1,3})[ :.](\d{2})(?:[ :.](\d{1,3}))?]""")

    fun parse(raw: String?, duration: Long = 0): LrcDocument {
        if (raw.isNullOrBlank()) return LrcDocument()

        val entries = mutableListOf<LyricLine>()
        val meta = mutableMapOf<String, String>()

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("[")) return@forEach

            if (LINE_VALIDATOR.matcher(trimmed).matches()) {
                val tagMatcher = TAG_PATTERN.matcher(trimmed)
                val tempTimes = mutableListOf<Long>()
                var lastTagEnd = 0

                while (tagMatcher.find()) {
                    if (tagMatcher.start() != lastTagEnd) {
                        break
                    }
                    tempTimes.add(
                        toMs(
                            tagMatcher.group(1),
                            tagMatcher.group(2),
                            tagMatcher.group(3)
                        )
                    )
                    lastTagEnd = tagMatcher.end()
                }

                val content = trimmed.substring(lastTagEnd).trim()

                tempTimes.forEach { time ->
                    entries.add(LyricLine(begin = time, text = content))
                }
            } else {
                parseMeta(trimmed, meta)
            }
        }
        return finalize(entries, meta, duration)
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

    private fun parseMeta(line: String, meta: MutableMap<String, String>) {
        val colon = line.indexOf(":")
        if (colon > 1 && line.endsWith("]")) {
            val key = line.substring(1, colon).trim()
            val value = line.substring(colon + 1, line.length - 1).trim()
            meta[key] = value
        }
    }

    private fun finalize(list: List<LyricLine>, meta: Map<String, String>, dur: Long): LrcDocument {
        val sorted = list.sortedBy { it.begin }
        val lines = sorted.mapIndexed { i, cur ->
            val next = sorted.getOrNull(i + 1)?.begin
            val end = next ?: if (dur > cur.begin) dur else cur.begin + 5000L
            cur.copy(end = end, duration = end - cur.begin)
        }

        val offset = meta["offset"]?.toLongOrNull() ?: 0L
        return LrcDocument(meta, lines).run {
            if (offset != 0L) applyOffset(offset) else this
        }
    }
}
