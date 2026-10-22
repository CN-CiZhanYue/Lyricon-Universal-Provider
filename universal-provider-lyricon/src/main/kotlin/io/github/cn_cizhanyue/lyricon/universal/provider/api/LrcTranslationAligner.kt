package io.github.cn_cizhanyue.lyricon.universal.provider.api

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.LrcParser
import kotlin.math.abs

internal object LrcTranslationAligner {
    private const val MAX_WINDOW_MS = 1500L

    fun align(primaryLrc: String, translationLrc: String?): String? {
        if (translationLrc.isNullOrBlank()) return null
        val primary = LrcParser.parse(primaryLrc).lines.filter { !it.text.isNullOrBlank() }
        val translation = LrcParser.parse(translationLrc).lines.filter { !it.text.isNullOrBlank() }
        if (primary.isEmpty() || translation.isEmpty()) return null
        var transIndex = 0
        val aligned = primary.mapIndexedNotNull { index, line ->
            val window = alignmentWindow(primary, index)
            while (transIndex < translation.size && translation[transIndex].begin < line.begin - window) {
                transIndex += 1
            }
            val candidate = translation.getOrNull(transIndex) ?: return@mapIndexedNotNull null
            if (abs(candidate.begin - line.begin) > window) return@mapIndexedNotNull null
            transIndex += 1
            val text = candidate.text?.trim().orEmpty()
            if (text.isBlank() || text == "//" || text == line.text?.trim()) return@mapIndexedNotNull null
            "[" + WordTimedLyricConverter.formatMs(line.begin) + "]" + text
        }
        return aligned.takeIf { it.size >= 2 }?.joinToString("\n")
    }

    private fun alignmentWindow(
        primary: List<io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricLine>,
        index: Int
    ): Long {
        val current = primary[index].begin
        val neighbor = listOfNotNull(
            primary.getOrNull(index - 1)?.begin,
            primary.getOrNull(index + 1)?.begin
        ).map { abs(it - current) }
        return neighbor.minOrNull()?.div(2)?.coerceIn(400L, MAX_WINDOW_MS) ?: MAX_WINDOW_MS
    }
}

