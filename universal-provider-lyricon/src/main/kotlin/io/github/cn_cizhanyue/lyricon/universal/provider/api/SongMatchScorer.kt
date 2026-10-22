package io.github.cn_cizhanyue.lyricon.universal.provider.api

object SongMatchScorer {
    fun calculate(
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long?,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long?
    ): Double {
        val cleanTargetTitle = cleanText(targetTitle)
        val cleanCandidateTitle = cleanText(candidateTitle)
        if (cleanTargetTitle.isEmpty() || cleanCandidateTitle.isEmpty()) return 0.0

        val titleScore = when {
            cleanTargetTitle == cleanCandidateTitle -> 1.0
            cleanCandidateTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanCandidateTitle) -> 0.85
            else -> diceCoefficient(cleanTargetTitle, cleanCandidateTitle)
        }

        if (titleScore < 0.35) return 0.0

        val cleanTargetArtist = cleanText(targetArtist)
        val cleanCandidateArtist = cleanText(candidateArtist)
        val artistScore = when {
            cleanTargetArtist.isEmpty() || cleanCandidateArtist.isEmpty() -> 0.5
            cleanTargetArtist == cleanCandidateArtist -> 1.0
            cleanTargetArtist.contains(cleanCandidateArtist) || cleanCandidateArtist.contains(cleanTargetArtist) -> 0.8
            else -> diceCoefficient(cleanTargetArtist, cleanCandidateArtist)
        }

        // Hard rejection if artists are known and completely different (e.g. Ed Sheeran vs Alexx)
        if (cleanTargetArtist.isNotEmpty() && cleanCandidateArtist.isNotEmpty()) {
            if (artistScore < 0.35) {
                return 0.0
            }
        }

        var durationBonus = 0.0
        if (targetDurationMs != null && candidateDurationMs != null && targetDurationMs > 0 && candidateDurationMs > 0) {
            val diffSec = kotlin.math.abs(targetDurationMs - candidateDurationMs) / 1000
            durationBonus = when {
                diffSec <= 3 -> 0.2
                diffSec <= 8 -> 0.1
                diffSec > 35 -> -0.3
                else -> 0.0
            }
        }

        return (titleScore * 0.6 + artistScore * 0.2 + durationBonus).coerceIn(0.0, 1.0)
    }

    private fun cleanText(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")
    }

    private fun diceCoefficient(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.length < 2 || s2.length < 2) return 0.0
        val pairs1 = (0 until s1.length - 1).map { s1.substring(it, it + 2) }.toSet()
        val pairs2 = (0 until s2.length - 1).map { s2.substring(it, it + 2) }.toSet()
        val intersection = pairs1.intersect(pairs2).size
        return (2.0 * intersection) / (pairs1.size + pairs2.size)
    }

    private val PURE_MUSIC_PATTERNS = listOf(
        "纯音乐",
        "没有填词的纯音乐",
        "请欣赏",
        "instrumental",
        "no lyrics"
    )

    private val CREDIT_PREFIXES = listOf(
        "作词", "作曲", "编曲", "制作人", "混音", "母带", "吉他", "贝斯", "鼓", "和声",
        "录音", "written by", "produced by", "arranged by", "composed by", "mixed by",
        "mastered by", "vocals by", "lyrics by", "music by"
    )

    fun isInstrumentalOrCreditsOnly(lrcText: String): Boolean {
        if (lrcText.isBlank()) return true
        val lines = lrcText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var lyricalCount = 0

        for (line in lines) {
            if (METADATA_LINE.matches(line)) continue
            val textOnly = line
                .replace(TIMESTAMP, "")
                .replace(WORD_STAMP, "")
                .trim()
            if (textOnly.isEmpty()) continue
            val lower = textOnly.lowercase()
            if (PURE_MUSIC_PATTERNS.any { lower.contains(it) }) {
                return true
            }
            val isCredit = CREDIT_PREFIXES.any { prefix ->
                lower.startsWith(prefix) || lower.startsWith("$prefix:") || lower.startsWith("$prefix：")
            }
            if (!isCredit) {
                lyricalCount++
            }
        }
        return lyricalCount < 2
    }

    private val TIMESTAMP = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    private val WORD_STAMP = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val METADATA_LINE = Regex("""^\[[a-zA-Z]{2,6}:.*]$""")
}
