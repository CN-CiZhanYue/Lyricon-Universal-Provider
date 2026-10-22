/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.policy

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import kotlin.math.abs

object LyricLaneAlignmentPolicy {

    private val PROMO_PATTERNS = listOf(
        Regex(""".*听歌就在.*酷狗.*星耀计划.*"""),
        Regex(""".*QQ音乐.*听歌.*"""),
        Regex(""".*网易云音乐.*特别呈现.*""")
    )

    fun align(
        sourceLines: List<RichLyricLine>,
        translationLines: List<RichLyricLine> = emptyList(),
        filterPromo: Boolean = true
    ): List<RichLyricLine> {
        if (sourceLines.isEmpty()) return emptyList()

        val filteredSource = if (filterPromo) {
            sourceLines.filterNot { isPromoLine(it.text) }
        } else {
            sourceLines
        }

        val filteredTrans = if (filterPromo) {
            translationLines.filterNot { isPromoLine(it.text) }
        } else {
            translationLines
        }

        val matchedTrans = associateTranslations(filteredSource, filteredTrans)

        var lastBegin = 0L
        return filteredSource.mapIndexed { index, line ->
            val monotonicBegin = line.begin.coerceAtLeast(lastBegin)
            lastBegin = monotonicBegin
            val monotonicEnd = line.end.coerceAtLeast(monotonicBegin)
            val duration = (monotonicEnd - monotonicBegin).coerceAtLeast(0L)

            val rawTrans = matchedTrans[index]?.text?.trim()
            val cleanTrans = if (rawTrans.isNullOrEmpty() || rawTrans == "//") null else rawTrans

            line.copy(
                begin = monotonicBegin,
                end = monotonicEnd,
                duration = duration,
                secondary = cleanTrans
            )
        }
    }

    private fun isPromoLine(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return PROMO_PATTERNS.any { it.matches(trimmed) }
    }

    private fun associateTranslations(
        sourceLines: List<RichLyricLine>,
        translationLines: List<RichLyricLine>
    ): List<RichLyricLine?> {
        if (sourceLines.isEmpty() || translationLines.isEmpty()) {
            return List(sourceLines.size) { null }
        }

        val result = arrayOfNulls<RichLyricLine>(sourceLines.size)
        var transIdx = 0

        for (srcIdx in sourceLines.indices) {
            val src = sourceLines[srcIdx]
            var bestTrans: RichLyricLine? = null
            var bestDelta = Long.MAX_VALUE

            var searchIdx = transIdx
            while (searchIdx < translationLines.size) {
                val trans = translationLines[searchIdx]
                val delta = abs(src.begin - trans.begin)
                if (delta <= 1500L) {
                    if (delta < bestDelta) {
                        bestDelta = delta
                        bestTrans = trans
                        transIdx = searchIdx + 1
                    }
                } else if (trans.begin > src.begin + 1500L) {
                    break
                }
                searchIdx++
            }

            result[srcIdx] = bestTrans
        }

        return result.asList()
    }
}
