package io.github.cn_cizhanyue.lyricon.universal.provider.api

import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import org.json.JSONObject








class SharedCacheSource : LyricsSource {

    override val name: String = "SHARED_CACHE"

    override fun fetchLyrics(query: TrackQuery): LyricResult? {
        val payload = UniversalLyricCache.get(query.title, query.artist) ?: return null
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (json.optBoolean("noLyric", false)) {
            return null
        }
        val lyric = json.optString("lyric").ifBlank { json.optString("rawLyric") }
        if (lyric.isBlank()) {
            return null
        }
        return LyricResult(
            title = json.optString("songName").ifBlank { query.title },
            artist = json.optString("artist").ifBlank { query.artist },
            lyric = lyric,
            rawLyric = json.optString("rawLyric").takeIf { it.isNotBlank() },
            translationLyric = json.optString("translationLyric").takeIf { it.isNotBlank() },
            source = "shared-cache",
        )
    }
}
