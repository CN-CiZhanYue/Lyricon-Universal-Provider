package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PaxsenixSource(private val client: OkHttpClient) : LyricsSource {
    override val name: String = "paxsenix/apple_music"
    private val appleIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun fetchLyrics(query: TrackQuery): LyricResult? = runCatching {
        val key = "${query.title.trim().lowercase()}|${query.artist.trim().lowercase()}|${query.durationMs ?: 0L}"
        val trackId = appleIdCache[key] ?: searchAppleTrackId(query)?.also { appleIdCache[key] = it } ?: return null
        val url = "https://lyrics.paxsenix.org/apple-music/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("id", trackId).addQueryParameter("ttml", "true")
            .addQueryParameter("v", "2").build()
        val response = client.newCall(Request.Builder().url(url).header("User-Agent", "UniversalLyricsProvider/1.0").build()).execute()
        if (!response.isSuccessful) return null
        val root = JSONObject(response.body?.string().orEmpty())
        val ttml = root.optString("content").ifBlank { root.optString("ttml") }
        val enhanced = BetterLyricsSource(client).ttmlToLrc(ttml)
        if (enhanced.isBlank()) return null
        LyricResult(query.title, query.artist, enhanced, enhanced, source = "universal/paxsenix/apple_music", sourceTrackId = trackId)
    }.getOrNull()

    private fun searchAppleTrackId(query: TrackQuery): String? {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", "${query.title} ${query.artist}".trim())
            .addQueryParameter("entity", "song").addQueryParameter("limit", "20").build()
        val response = client.newCall(Request.Builder().url(url).header("User-Agent", "UniversalLyricsProvider/1.0").build()).execute()
        if (!response.isSuccessful) return null
        val rows = JSONObject(response.body?.string().orEmpty()).optJSONArray("results") ?: return null
        var bestId: String? = null
        var bestScore = 0.0
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val score = SongMatchScorer.calculate(
                query.title, query.artist, query.durationMs,
                row.optString("trackName"), row.optString("artistName"),
                row.optLong("trackTimeMillis").takeIf { it > 0L }
            )
            if (score > bestScore) {
                bestScore = score
                bestId = row.optLong("trackId").takeIf { it > 0L }?.toString()
            }
        }
        return bestId.takeIf { bestScore >= 0.72 }
    }
}
