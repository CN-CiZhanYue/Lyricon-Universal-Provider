package io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller

import android.content.Context
import android.content.Intent
import io.github.cn_cizhanyue.lyricon.universal.provider.NoLyricPolicy
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppNetwork
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator
import io.github.cn_cizhanyue.lyricon.universal.provider.api.SearchCandidate
import org.json.JSONObject
import java.util.Locale

class SearchController(private val context: Context) {

    suspend fun search(query: String): List<SearchCandidate> {
        if (query.isBlank()) return emptyList()
        UniversalAppNetwork.initialize(context)
        val aggregator = LyricsSourceAggregator.shared
        return aggregator.qqSource.searchCandidates(query) +
            aggregator.netEaseSource.searchCandidates(query) +
            aggregator.appleMusicSource.searchCandidates(query)
    }

    suspend fun bind(candidate: SearchCandidate, current: CurrentTrack): Boolean {
        UniversalAppNetwork.initialize(context)
        val aggregator = LyricsSourceAggregator.shared
        val source = when {
            candidate.source.contains("QQ") -> aggregator.qqSource
            candidate.source.contains("Apple") -> aggregator.appleMusicSource
            else -> aggregator.netEaseSource
        }
        val result = source.fetchLyricByCandidate(candidate) ?: return false
        if (result.lyric.isBlank()) return false
        val json = JSONObject().apply {
            put("songName", current.title.ifBlank { result.title })
            put("artist", current.artist.ifBlank { result.artist })
            put("album", candidate.album)
            put("songId", "universal-${current.generation}")
            put("lyricType", 0)
            put("id", "")
            put("noLyric", false)
            put("lyric", result.lyric)
            put("rawLyric", result.rawLyric ?: result.lyric)
            if (!result.translationLyric.isNullOrBlank()) {
                put("transLyric", result.translationLyric)
                put("translationLyric", result.translationLyric)
                put("translationLanguageTag", Locale.getDefault().toLanguageTag())
            }
            put("provider", "io.github.cn_cizhanyue.lyricon.universal.provider")
            put("source", result.source + "/manual")
            put("sessionGeneration", current.generation)
            put("remark", "[CLLUniversal]")
        }.toString()
        UniversalLyricCache.putManualBinding(context, current.title, current.artist, json)
        inject(current, json)
        return true
    }

    suspend fun markNoLyric(current: CurrentTrack): Boolean {
        val json = NoLyricPolicy.payloadJson(current.title, current.artist, NoLyricPolicy.REASON_USER)
        UniversalLyricCache.putManualBinding(context, current.title, current.artist, json)
        inject(current, json)
        return true
    }

    private fun inject(current: CurrentTrack, json: String) {
        context.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC).apply {
            putExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE, current.packageName)
            putExtra(UniversalSnapshotStore.EXTRA_GENERATION, current.generation)
            putExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO, json)
            putExtra("extra_is_manual", true)
            putExtra("extra_track_key", "${current.title.trim()}|${current.artist.trim()}".lowercase())
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(0x01000000)
        })
    }
}
