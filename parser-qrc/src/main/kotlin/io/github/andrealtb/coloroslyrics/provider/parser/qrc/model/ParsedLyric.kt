/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.qrc.model

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.LrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LrcDocument
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.qrc.QrcParser
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class ParsedLyric(
    val lyricsRaw: String? = null,
    val translationRaw: String? = null,
    val romaRaw: String? = null
) {
    val richLyricLines: List<RichLyricLine> by lazy {
        val raw = lyricsRaw?.takeIf { it.isNotBlank() } ?: return@lazy emptyList()

        val transIndex = translationData.lines
        val romaIndex = lrcRomaData.lines

        val sourceLines = runCatching { QrcParser.parseXML(raw).firstOrNull()?.lines }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: lrcDocument.lines
        val matchedTranslations = associateQrcTranslations(sourceLines, transIndex)

        sourceLines.mapIndexed { index, line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                secondary = matchedTranslations[index]
                    ?.text
                    ?.takeUnless { it.trim() == "//" },
                words = null
            )
        }
    }

    private fun associateQrcTranslations(
        sourceLines: List<LyricLine>,
        translationLines: List<LyricLine>
    ): List<LyricLine?> {
        if (sourceLines.isEmpty() || translationLines.isEmpty()) {
            return List(sourceLines.size) { null }
        }

        val sourceCount = sourceLines.size
        val translationCount = translationLines.size
        val matchedCounts = Array(sourceCount + 1) { IntArray(translationCount + 1) }
        val timestampCosts = Array(sourceCount + 1) { LongArray(translationCount + 1) }
        val steps = Array(sourceCount + 1) { IntArray(translationCount + 1) }

        for (sourceIndex in 1..sourceCount) {
            steps[sourceIndex][0] = SKIP_SOURCE
        }
        for (translationIndex in 1..translationCount) {
            steps[0][translationIndex] = SKIP_TRANSLATION
        }

        for (sourceIndex in 1..sourceCount) {
            for (translationIndex in 1..translationCount) {
                var bestMatchCount = matchedCounts[sourceIndex - 1][translationIndex]
                var bestTimestampCost = timestampCosts[sourceIndex - 1][translationIndex]
                var bestStep = SKIP_SOURCE

                val skipTranslationMatchCount = matchedCounts[sourceIndex][translationIndex - 1]
                val skipTranslationTimestampCost = timestampCosts[sourceIndex][translationIndex - 1]
                if (isBetterAlignment(
                        skipTranslationMatchCount,
                        skipTranslationTimestampCost,
                        bestMatchCount,
                        bestTimestampCost
                    )
                ) {
                    bestMatchCount = skipTranslationMatchCount
                    bestTimestampCost = skipTranslationTimestampCost
                    bestStep = SKIP_TRANSLATION
                }

                val source = sourceLines[sourceIndex - 1]
                val translation = translationLines[translationIndex - 1]
                val timestampDelta = abs(source.begin - translation.begin)
                if (timestampDelta <= qrcTranslationAlignmentWindow(sourceLines, sourceIndex - 1)) {
                    val matchCount = matchedCounts[sourceIndex - 1][translationIndex - 1] + 1
                    val timestampCost = timestampCosts[sourceIndex - 1][translationIndex - 1] + timestampDelta
                    if (isBetterAlignment(matchCount, timestampCost, bestMatchCount, bestTimestampCost)) {
                        bestMatchCount = matchCount
                        bestTimestampCost = timestampCost
                        bestStep = MATCH
                    }
                }

                matchedCounts[sourceIndex][translationIndex] = bestMatchCount
                timestampCosts[sourceIndex][translationIndex] = bestTimestampCost
                steps[sourceIndex][translationIndex] = bestStep
            }
        }

        val result = arrayOfNulls<LyricLine>(sourceCount)
        var sourceIndex = sourceCount
        var translationIndex = translationCount
        while (sourceIndex > 0 && translationIndex > 0) {
            when (steps[sourceIndex][translationIndex]) {
                MATCH -> {
                    result[sourceIndex - 1] = translationLines[translationIndex - 1]
                    sourceIndex--
                    translationIndex--
                }

                SKIP_SOURCE -> sourceIndex--
                else -> translationIndex--
            }
        }
        return result.asList()
    }

    private fun qrcTranslationAlignmentWindow(sourceLines: List<LyricLine>, index: Int): Long {
        val begin = sourceLines[index].begin
        val neighborDistances = listOfNotNull(
            sourceLines.getOrNull(index - 1)?.begin,
            sourceLines.getOrNull(index + 1)?.begin
        ).map { abs(it - begin) }

        return neighborDistances.minOrNull()
            ?.div(2)
            ?.coerceAtMost(MAX_QRC_TRANSLATION_TIMESTAMP_DRIFT_MS)
            ?: MAX_QRC_TRANSLATION_TIMESTAMP_DRIFT_MS
    }

    private fun isBetterAlignment(
        candidateMatchCount: Int,
        candidateTimestampCost: Long,
        currentMatchCount: Int,
        currentTimestampCost: Long
    ): Boolean = candidateMatchCount > currentMatchCount ||
        (candidateMatchCount == currentMatchCount && candidateTimestampCost < currentTimestampCost)

    private val lrcDocument: LrcDocument by lazy {
        val raw = lyricsRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    private val translationData: LrcDocument by lazy {
        val raw = translationRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    private val lrcRomaData: LrcDocument by lazy {
        val raw = romaRaw
        if (raw.isNullOrBlank()) LrcDocument() else LrcParser.parse(raw)
    }

    private companion object {
        const val MAX_QRC_TRANSLATION_TIMESTAMP_DRIFT_MS = 1_200L

        const val SKIP_SOURCE = 1
        const val SKIP_TRANSLATION = 2
        const val MATCH = 3
    }
}
