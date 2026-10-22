package io.github.cn_cizhanyue.lyricon.universal.provider

import org.json.JSONObject

/**
 * Applies the lyric-processing switches when a payload is published; caches keep the full payload.
 *
 * `rawLyric` is the Bridge model channel, and Bridge builds its model (including the translation
 * lane) only from a timed `rawLyric`. Word timing therefore only chooses word- or line-timed
 * content; the rawLyric switch alone hands rendering back to the official SystemUI renderer.
 */
internal object UniversalLyricPayloadPolicy {
    private val WORD_STAMP = Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>")
    private val LINE_STAMP = Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
    private val TRANSLATION_KEYS = listOf("transLyric", "translationLyric", "translationLanguageTag")

    data class Result(
        val hadRaw: Boolean,
        val hadWordTiming: Boolean,
        val rawAttached: Boolean,
        val wordTimed: Boolean
    )

    fun apply(json: JSONObject, wordTiming: Boolean, rawLyric: Boolean, translation: Boolean): Result {
        val raw = json.optString("rawLyric")
        val plain = stripWordTiming(json.optString("lyric"))
        if (plain.isNotBlank()) json.put("lyric", plain)
        val hadWordTiming = hasWordTiming(raw)
        val published = when {
            !rawLyric -> ""
            wordTiming && hadWordTiming -> raw
            hasLineTiming(raw) -> stripWordTiming(raw)
            // Payloads cached without rawLyric still feed the Bridge line model.
            hasLineTiming(plain) -> plain
            else -> ""
        }
        if (published.isNotBlank()) json.put("rawLyric", published) else json.remove("rawLyric")
        if (!translation) TRANSLATION_KEYS.forEach(json::remove)
        return Result(
            hadRaw = raw.isNotBlank(),
            hadWordTiming = hadWordTiming,
            rawAttached = published.isNotBlank(),
            wordTimed = hasWordTiming(published)
        )
    }

    fun stripWordTiming(value: String): String = value.replace(WORD_STAMP, "")

    private fun hasWordTiming(value: String): Boolean = WORD_STAMP.containsMatchIn(value)

    private fun hasLineTiming(value: String): Boolean = LINE_STAMP.containsMatchIn(value)
}
