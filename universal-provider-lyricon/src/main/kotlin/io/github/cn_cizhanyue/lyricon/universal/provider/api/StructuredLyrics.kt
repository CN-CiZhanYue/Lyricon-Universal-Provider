package io.github.cn_cizhanyue.lyricon.universal.provider.api

internal data class TimedWord(val startMs: Long, val endMs: Long, val text: String)
internal data class TimedLine(val startMs: Long, val endMs: Long, val words: List<TimedWord>) {
    val text: String get() = words.joinToString("") { it.text }
}

internal object StructuredLyrics {
    private val QRC_XML = Regex("""LyricContent\s*=\s*"([\s\S]*?)"(?=\s*/?>)""")
    private val META = Regex("""^\[(\w+):([^]]*)]$""")
    private val QRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val QRC_WORD = Regex("""((?:(?!\(\d+,\d+\)).)*)\((\d+),(\d+)\)""")
    private val YRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val YRC_WORD = Regex("""\((\d+),(\d+),\d+\)([^()]*)""")
    private val LRC_TIME = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parseQrc(text: String?): List<TimedLine> {
        if (text.isNullOrBlank()) return emptyList()
        var content: String = text
        QRC_XML.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
            content = raw.replace("&quot;", "\"")
                .replace("&apos;", "''")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        }
        val result = mutableListOf<TimedLine>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || META.matches(line)) return@forEach
            val match = QRC_LINE.matchEntire(line) ?: return@forEach
            val start = match.groupValues[1].toLongOrNull() ?: return@forEach
            val duration = match.groupValues[2].toLongOrNull() ?: 0L
            val end = start + duration
            val body = match.groupValues[3]
            val words = mutableListOf<TimedWord>()
            QRC_WORD.findAll(body).forEach { word ->
                val wordStart = word.groupValues[2].toLongOrNull() ?: start
                val wordDur = word.groupValues[3].toLongOrNull() ?: 0L
                words += TimedWord(wordStart, wordStart + wordDur, word.groupValues[1])
            }
            if (words.isEmpty() && body.isNotBlank()) {
                words += TimedWord(start, end, body)
            }
            if (words.isNotEmpty()) result += TimedLine(start, end, words)
        }
        return result.sortedBy { it.startMs }
    }

    fun parseYrc(text: String?): List<TimedLine> {
        if (text.isNullOrBlank()) return emptyList()
        val result = mutableListOf<TimedLine>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val match = YRC_LINE.matchEntire(line) ?: return@forEach
            val start = match.groupValues[1].toLongOrNull() ?: return@forEach
            val duration = match.groupValues[2].toLongOrNull() ?: 0L
            val end = start + duration
            val body = match.groupValues[3]
            val words = mutableListOf<TimedWord>()
            YRC_WORD.findAll(body).forEach { word ->
                val wordStart = word.groupValues[1].toLongOrNull() ?: start
                val wordDur = word.groupValues[2].toLongOrNull() ?: 0L
                words += TimedWord(wordStart, wordStart + wordDur, word.groupValues[3])
            }
            if (words.isEmpty() && body.isNotBlank()) {
                words += TimedWord(start, end, body)
            }
            if (words.isNotEmpty()) result += TimedLine(start, end, words)
        }
        return result.sortedBy { it.startMs }
    }

    fun parseLrc(text: String?): List<TimedLine> {
        if (text.isNullOrBlank()) return emptyList()
        val timed = mutableListOf<Pair<Long, String>>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || META.matches(line)) return@forEach
            val matches = LRC_TIME.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val last = matches.last()
            val content = line.substring(last.range.last + 1).trim()
            if (content.isEmpty() || content == "//") return@forEach
            matches.forEach { match ->
                timed += lrcTimeMs(match) to content
            }
        }
        timed.sortBy { it.first }
        return timed.mapIndexed { index, item ->
            val end = timed.getOrNull(index + 1)?.first?.minus(10)?.coerceAtLeast(item.first) ?: (item.first + 3000)
            TimedLine(item.first, end, listOf(TimedWord(item.first, end, item.second)))
        }
    }

    fun merge(original: List<TimedLine>, translation: List<TimedLine>): List<Pair<TimedLine, String>> {
        if (original.isEmpty()) return emptyList()
        val sorted = translation.filter { !isCreditOrMeta(it.text) }.sortedBy { it.startMs }
        val sungIdx = original.indices.filter { !isCreditOrMeta(original[it].text) }
        if (sungIdx.isEmpty() || sorted.isEmpty()) {
            return original.map { it to "" }
        }
        if (sungIdx.size == sorted.size) {
            val byIndex = HashMap<Int, String>(sungIdx.size)
            sungIdx.forEachIndexed { i, origIdx ->
                byIndex[origIdx] = sorted[i].text.trim()
            }
            return original.mapIndexed { index, line -> line to (byIndex[index] ?: "") }
        }
        val assigned = MutableList(original.size) { "" }
        val usedSung = mutableSetOf<Int>()
        val usedTrans = mutableSetOf<Int>()
        for ((transIndex, tr) in sorted.withIndex()) {
            var best = -1
            var bestDist = Long.MAX_VALUE
            for (idx in sungIdx) {
                if (idx in usedSung) continue
                val dist = kotlin.math.abs(original[idx].startMs - tr.startMs)
                if (dist <= alignmentWindow(original, idx) && dist < bestDist) {
                    bestDist = dist
                    best = idx
                }
            }
            if (best >= 0) {
                assigned[best] = tr.text.trim()
                usedSung += best
                usedTrans += transIndex
            }
        }
        var rest = 0
        for (idx in sungIdx) {
            if (assigned[idx].isNotBlank()) continue
            while (rest < sorted.size && rest in usedTrans) rest += 1
            if (rest >= sorted.size) break
            assigned[idx] = sorted[rest].text.trim()
            usedTrans += rest
            rest += 1
        }
        return original.mapIndexed { index, line -> line to assigned[index] }
    }

    fun isCreditOrMeta(text: String): Boolean {
        val normalized = text.trim().lowercase().replace("\uFF1A", ":")
        if (normalized.isEmpty()) return true
        if (TRACK_HEADER.matches(text.trim())) return true
        return CREDIT_PREFIXES.any { prefix ->
            normalized.startsWith(prefix) ||
                normalized.startsWith("$prefix:") ||
                normalized.startsWith("$prefix :")
        }
    }

    private fun nextSungStartMs(original: List<TimedLine>, index: Int): Long {
        for (i in (index + 1) until original.size) {
            if (!isCreditOrMeta(original[i].text)) return original[i].startMs
        }
        return Long.MAX_VALUE
    }

    private fun alignmentWindow(original: List<TimedLine>, index: Int): Long {
        val current = original[index].startMs
        val neighbors = listOfNotNull(
            original.getOrNull(index - 1)?.startMs,
            original.getOrNull(index + 1)?.startMs
        ).map { kotlin.math.abs(it - current) }
        return neighbors.minOrNull()?.div(2)?.coerceIn(800L, 4000L) ?: 4000L
    }

    private val TRACK_HEADER = Regex("^.{1,80}\\s[-–—]\\s.{1,80}$")

    private val CREDIT_PREFIXES = listOf(
        "作词", "作曲", "编曲", "制作人", "混音", "母带", "录音",
        "written by", "produced by", "composed by", "arranged by",
        "lyrics by", "music by", "mixed by", "mastered by", "vocals by"
    )

    fun toPlainLrc(lines: List<TimedLine>): String =
        lines.filter { it.text.isNotBlank() }
            .joinToString("\n") { "[" + WordTimedLyricConverter.formatMs(it.startMs) + "]" + it.text }

    fun toEnhancedLrc(lines: List<TimedLine>): String {
        return lines.joinToString("\n") { line ->
            buildString {
                append("[").append(WordTimedLyricConverter.formatMs(line.startMs)).append("]")
                if (line.words.size > 1) {
                    line.words.forEach { word ->
                        append("<").append(WordTimedLyricConverter.formatMs(word.startMs)).append(">")
                        append(word.text)
                    }
                    append("<").append(WordTimedLyricConverter.formatMs(line.endMs)).append(">")
                } else {
                    append(line.text)
                }
            }
        }
    }

    fun toTranslationLrc(merged: List<Pair<TimedLine, String>>): String =
        merged.filter { it.second.isNotBlank() && it.second != "//" && it.second != it.first.text.trim() }
            .joinToString("\n") { "[" + WordTimedLyricConverter.formatMs(it.first.startMs) + "]" + it.second }

    fun hasWordTiming(lines: List<TimedLine>): Boolean = lines.any { it.words.size > 1 }

    private fun lrcTimeMs(match: MatchResult): Long {
        val min = match.groupValues[1].toLongOrNull() ?: 0L
        val sec = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3]
        val ms = when {
            fraction.isBlank() -> 0L
            fraction.length == 2 -> fraction.toLong() * 10
            else -> fraction.padEnd(3, "0".first()).take(3).toLong()
        }
        return min * 60_000 + sec * 1_000 + ms
    }
}

