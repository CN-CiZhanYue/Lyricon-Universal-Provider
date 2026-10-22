package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class BetterLyricsSource(private val client: OkHttpClient) : LyricsSource {
    override val name: String = "better_lyrics"

    override fun fetchLyrics(query: TrackQuery): LyricResult? {
        return runCatching {
            val urlBuilder = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
                .addQueryParameter("s", query.title)
                .addQueryParameter("a", query.artist)
            query.album?.takeIf { it.isNotBlank() }?.let { urlBuilder.addQueryParameter("al", it) }
            query.durationMs?.let { urlBuilder.addQueryParameter("d", (it / 1000).toString()) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("User-Agent", "UniversalLyricsProvider/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val lrc = json.optString("lrc").ifBlank { json.optString("lyrics") }
                .ifBlank { ttmlToLrc(json.optString("ttml")) }
            if (lrc.isBlank()) return null
            val translation = UnisonTranslationSource(client).translate(
                lrc,
                java.util.Locale.getDefault().language
            )

            LyricResult(
                title = query.title,
                artist = query.artist,
                lyric = lrc,
                rawLyric = lrc,
                translationLyric = translation,
                source = "universal/better_lyrics"
            )
        }.getOrNull()
    }

    internal fun ttmlToLrc(ttml: String): String {
        if (ttml.isBlank()) return ""
        val rows = Regex("<p\\b[^>]*\\bbegin=\\\"([^\\\"]+)\\\"[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)
        return rows.findAll(ttml).mapNotNull { match ->
            val lineStamp = toLrcStamp(match.groupValues[1]) ?: return@mapNotNull null
            val inner = match.groupValues[2]
            val spans = Regex("<span\\b[^>]*\\bbegin=\\\"([^\\\"]+)\\\"(?:[^>]*\\bend=\\\"([^\\\"]+)\\\")?[^>]*>([\\s\\S]*?)</span>", RegexOption.IGNORE_CASE)
                .findAll(inner).toList()
            if (spans.isEmpty()) {
                val text = decodeXml(inner.replace(Regex("<[^>]+>"), "")).trim()
                if (text.isBlank()) null else "[$lineStamp]$text"
            } else {
                val builder = StringBuilder("[").append(lineStamp).append(']')
                var cursor = 0
                spans.forEach { span ->
                    val between = cleanInterSpan(inner.substring(cursor, span.range.first))
                    if (between.isNotEmpty()) builder.append(between)
                    val wordStamp = toLrcStamp(span.groupValues[1]) ?: return@forEach
                    val text = decodeXml(span.groupValues[3].replace(Regex("<[^>]+>"), ""))
                    if (text.isNotBlank()) builder.append('<').append(wordStamp).append('>').append(text)
                    cursor = span.range.last + 1
                }
                val trailing = cleanInterSpan(inner.substring(cursor))
                if (trailing.isNotEmpty()) builder.append(trailing)
                val lineEnd = spans.lastOrNull()?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(::toLrcStamp)
                if (lineEnd != null) builder.append('<').append(lineEnd).append('>')
                builder.toString().takeIf { it.length > lineStamp.length + 2 }
            }
        }.joinToString("\n")
    }

    private fun decodeXml(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'")

    private fun cleanInterSpan(value: String): String = decodeXml(value.replace(Regex("<[^>]+>"), ""))
        .replace("\r", "").replace("\n", "").replace("\t", "")

    internal fun toLrcStamp(value: String): String? {
        val parts = value.split(":")
        if (parts.size == 1) {
            val totalSeconds = value.removeSuffix("s").toDoubleOrNull() ?: return null
            val minutes = (totalSeconds / 60).toInt()
            val seconds = totalSeconds - minutes * 60
            return "%02d:%06.3f".format(java.util.Locale.ROOT, minutes, seconds)
        }
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = (parts[parts.size - 2].toIntOrNull() ?: return null) +
            if (parts.size > 2) (parts[parts.size - 3].toIntOrNull() ?: 0) * 60 else 0
        return "%02d:%06.3f".format(java.util.Locale.ROOT, minutes, seconds)
    }
}
