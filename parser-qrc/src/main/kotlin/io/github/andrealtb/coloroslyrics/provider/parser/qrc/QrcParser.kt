/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.qrc

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.qrc.model.QrcData

/**
 * QRC Lyrics Parser.
 */
object QrcParser {

    private val linePattern = Regex("""\[(\d+)\s*,\s*(\d+)]""")
    private val wordPattern = Regex("""([^()\n\r]*)\((\d+)\s*,\s*(\d+)\)""")
    private val metaPattern = Regex("""\[(\w+)\s*:\s*([^]]*)]""")

    fun parseXML(xml: String?): List<QrcData> {
        if (xml.isNullOrBlank()) return emptyList()

        val pattern = Regex("""LyricContent\s*=\s*"([\s\S]*?)"(?=\s*/?>)""")

        return pattern.findAll(xml).mapNotNull { match ->
            val rawContent = match.groupValues[1]
            if (rawContent.isBlank()) {
                null
            } else {
                val decodedContent = decodeXmlEntities(rawContent)
                val (meta, lines) = parseLyric(decodedContent)
                QrcData(meta, lines)
            }
        }.toList()
    }

    fun parseLyric(content: String): Pair<Map<String, String>, List<LyricLine>> {
        val metaData = mutableMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()

        metaPattern.findAll(content).forEach {
            metaData[it.groupValues[1]] = it.groupValues[2].trim()
        }

        val lineMatches = linePattern.findAll(content).toList()

        for (i in lineMatches.indices) {
            val currentMatch = lineMatches[i]
            val lineStart = currentMatch.groupValues[1].toLongOrNull() ?: 0L
            val lineDur = currentMatch.groupValues[2].toLongOrNull() ?: 0L

            val bodyStart = currentMatch.range.last + 1
            val bodyEnd = if (i + 1 < lineMatches.size) {
                lineMatches[i + 1].range.first
            } else {
                val newlineIdx = content.indexOf('\n', bodyStart)
                val candidate = if (newlineIdx >= 0) newlineIdx else content.length
                if (candidate > bodyStart) candidate else content.length
            }

            if (bodyStart < bodyEnd) {
                var lineBody = content.substring(bodyStart, bodyEnd)
                if (lineBody.endsWith('\r')) lineBody = lineBody.dropLast(1)
                lines.add(parseLineBody(lineStart, lineDur, lineBody))
            }
        }

        return metaData to lines.sortedBy { it.begin }
    }

    private fun parseLineBody(lineStart: Long, lineDur: Long, rawBody: String): LyricLine {
        val trimmedBody = rawBody.trim('\n', '\r')
        val words = mutableListOf<LyricWord>()

        wordPattern.findAll(trimmedBody).forEach { match ->
            val text = match.groupValues[1]
            val wStart = match.groupValues[2].toLongOrNull() ?: 0L
            val wDur = match.groupValues[3].toLongOrNull() ?: 0L

            if (wDur >= 0) {
                words.add(
                    LyricWord(
                        begin = wStart,
                        end = wStart + wDur,
                        duration = wDur,
                        text = text
                    )
                )
            }
        }

        val finalText = if (words.isEmpty()) {
            trimmedBody.replace(Regex("""\(\d+,\d+\)"""), "").trim()
        } else {
            words.joinToString("") { it.text.orEmpty() }
        }

        return LyricLine(
            begin = lineStart,
            duration = lineDur,
            end = lineStart + lineDur,
            text = finalText
        )
    }

    private fun decodeXmlEntities(input: String): String {
        return input.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
    }
}
