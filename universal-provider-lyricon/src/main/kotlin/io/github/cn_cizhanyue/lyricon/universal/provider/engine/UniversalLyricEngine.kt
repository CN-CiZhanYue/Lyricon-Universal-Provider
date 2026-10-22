package io.github.cn_cizhanyue.lyricon.universal.provider.engine

import android.content.Context
import android.content.Intent
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppNetwork
import io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator
import io.github.cn_cizhanyue.lyricon.universal.provider.api.TrackQuery
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class UniversalLyricEngine(
    private val context: Context,
    private val aggregator: LyricsSourceAggregator = LyricsSourceAggregator()
) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var lastQueryKey: String? = null

    fun onTrackChanged(
        ownerPackage: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        generation: Long
    ) {
        if (title.isBlank()) return
        val queryKey = "${ownerPackage}|${title}|${artist}|$generation"
        if (queryKey == lastQueryKey) return
        lastQueryKey = queryKey

        // Check cache first
        val cachedJson = UniversalLyricCache.get(context, title, artist)
        if (!cachedJson.isNullOrBlank()) {
            val updatedJson = updateGeneration(cachedJson, generation)
            syncToSystem(ownerPackage, generation, updatedJson)
            return
        }

        executor.execute {
            UniversalAppNetwork.initialize(context)
            val query = TrackQuery(title, artist, album, durationMs)
            val report = aggregator.fetchFirst(query.copy(generation = generation))
            val result = report.result
            if (result != null && result.lyric.isNotBlank()) {
                val json = JSONObject().apply {
                    put("songName", result.title)
                    put("artist", result.artist)
                    put("album", album.orEmpty())
                    put("songId", "universal-$generation")
                    put("lyricType", 0)
                    put("id", "")
                    put("noLyric", false)
                    put("lyric", result.lyric)
                    put("rawLyric", result.rawLyric ?: result.lyric)
                    val translationOn = context.getSharedPreferences("LyricSourceSettingsActivity", android.content.Context.MODE_PRIVATE)
                        .getBoolean("translation_enabled", true)
                    if (translationOn && !result.translationLyric.isNullOrBlank()) {
                        put("transLyric", result.translationLyric)
                        put("translationLyric", result.translationLyric)
                        put("translationLanguageTag", Locale.getDefault().toLanguageTag())
                    }
                    put("provider", "io.github.cn_cizhanyue.lyricon.universal.provider")
                    put("source", result.source)
                    put("sessionGeneration", generation)
                    put("remark", "[CLLUniversal]")
                }.toString()

                UniversalLyricCache.put(context, title, artist, json)
                syncToSystem(ownerPackage, generation, json)
            }
        }
    }

    private fun updateGeneration(jsonStr: String, generation: Long): String {
        return runCatching {
            val json = JSONObject(jsonStr)
            json.put("sessionGeneration", generation)
            json.put("songId", "universal-$generation")
            json.toString()
        }.getOrDefault(jsonStr)
    }

    private fun syncToSystem(ownerPackage: String, generation: Long, lyricInfo: String) {
        val intent = Intent(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC).apply {
            putExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE, ownerPackage)
            putExtra(UniversalSnapshotStore.EXTRA_GENERATION, generation)
            putExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO, lyricInfo)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(0x01000000)
        }
        context.sendBroadcast(intent)
    }
}
