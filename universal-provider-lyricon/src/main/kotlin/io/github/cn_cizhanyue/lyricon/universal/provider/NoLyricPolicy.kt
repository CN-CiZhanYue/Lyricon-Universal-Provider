package io.github.cn_cizhanyue.lyricon.universal.provider

object NoLyricPolicy {
    const val REASON_USER = "userConfirmed"
    const val REASON_MISS = "confirmedMiss"
    const val REASON_CREDITS = "creditsOnly"

    enum class Outcome {
        PUBLISH_LYRICS,
        AUTO_NEGATIVE,
        TRANSIENT_RETRY
    }

    fun decide(hasUsableLyric: Boolean, instrumental: Boolean, transientFailure: Boolean): Outcome {
        if (hasUsableLyric && !instrumental) return Outcome.PUBLISH_LYRICS
        if (transientFailure) return Outcome.TRANSIENT_RETRY
        return Outcome.AUTO_NEGATIVE
    }

    fun autoReason(instrumental: Boolean): String =
        if (instrumental) REASON_CREDITS else REASON_MISS

    fun isNoLyric(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return json.contains("\"noLyric\":true")
    }

    fun isUserConfirmed(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return json.contains("\"noLyricReason\":\"$REASON_USER\"") ||
            (isNoLyric(json) && json.contains("userConfirmed"))
    }

    fun shouldSkipNetwork(json: String?): Boolean = isNoLyric(json)

    fun payloadJson(title: String, artist: String, reason: String): String =
        org.json.JSONObject().apply {
            put("songName", title)
            put("artist", artist)
            put("noLyric", true)
            put("lyric", "")
            put("noLyricReason", reason)
            put("provider", "io.github.cn_cizhanyue.lyricon.universal.provider")
            put("remark", "[CLLUniversal]")
        }.toString()
}
