package io.github.cn_cizhanyue.lyricon.universal.provider.lyricon

import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalLyricPayloadPolicy
import io.github.cn_cizhanyue.lyricon.universal.provider.api.WordTimedLyricConverter
import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.Song
import org.json.JSONObject









object LyriconBridge {

    private const val TAG = "LyriconUniversal"

    private val workerThread =
        HandlerThread("LyriconUniversalBridge", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val worker = Handler(workerThread.looper)
    private val publishers = HashMap<String, LyriconPublisher>()
    private val generations = HashMap<String, Long>()
    private val lastPayloadKeys = HashMap<String, String>()
    private val lastPlaceholderGenerations = HashMap<String, Long>()
    private val lastTitles = HashMap<String, String>()
    private val lastPlaceholderTitles = HashMap<String, String>()
    private val lastSessionIds = HashMap<String, String>()

    




    @JvmStatic
    fun publish(
        ownerPackage: String?,
        lyricInfo: String?,
        title: String?,
        artist: String?,
        durationMs: Long?,
        playback: PlaybackState?,
        generation: Long,
        wordTimingEnabled: Boolean,
        sessionId: String?,
    ) {
        if (ownerPackage.isNullOrBlank() || lyricInfo.isNullOrBlank()) return
        val payload = lyricInfo
        val songTitle = title.orEmpty()
        val songArtist = artist.orEmpty()
        val duration = durationMs ?: 0L
        worker.post {
            if (!acceptGeneration(ownerPackage, generation, songTitle, sessionId)) {
                Log.i(TAG, "ignoring stale lyricon payload for $ownerPackage gen=$generation")
                return@post
            }
            if (!payloadTitleMatches(payload, songTitle)) {
                Log.i(TAG, "ignoring stale-title payload $ownerPackage gen=$generation title=$songTitle")
                return@post
            }
            val payloadKey = "$generation|${payload.hashCode()}|${payload.length}"
            if (lastPayloadKeys[ownerPackage] == payloadKey) {
                
                
                return@post
            }
            lastPayloadKeys[ownerPackage] = payloadKey
            val payloadJson = runCatching { JSONObject(payload) }.getOrNull()
            val rawForLog = payloadJson?.optString("rawLyric").orEmpty()
            Log.i(
                TAG,
                "payload $ownerPackage gen=$generation wordTimingSetting=$wordTimingEnabled " +
                    "source=${payloadJson?.optString("source").orEmpty()} " +
                    "rawTags=${WordTimedLyricConverter.hasWordTiming(rawForLog)} rawLen=${rawForLog.length}",
            )
            val normalized = normalizePayload(payload)
            val parsed = LyricInfoParser.parse(normalized, songTitle, songArtist, duration)
            if (parsed == null) {
                Log.w(TAG, "lyricon payload rejected for $ownerPackage")
                return@post
            }
            publisherFor(ownerPackage).submit(
                parsed.dedupeKey(),
                parsed.toSong(),
                playback,
                parsed.hasTranslation,
                true,
            )
            val firstLines = parsed.lines.take(3).joinToString(",") { "${it.begin}-${it.end}" }
            Log.i(
                TAG,
                "lyricon lyrics for $ownerPackage lines=${parsed.lines.size} " +
                    "words=${parsed.hasWordTiming} synthetic=${parsed.hasSyntheticWords()} " +
                    "translation=${parsed.hasTranslation} first=$firstLines",
            )
        }
    }

    
    @JvmStatic
    fun placeholder(
        ownerPackage: String?,
        title: String?,
        artist: String?,
        durationMs: Long?,
        playback: PlaybackState?,
        generation: Long,
        sessionId: String?,
    ) {
        if (ownerPackage.isNullOrBlank() || title.isNullOrBlank()) return
        val songTitle = title
        val songArtist = artist.orEmpty()
        val duration = durationMs ?: 0L
        worker.post {
            val lastGen = lastPlaceholderGenerations[ownerPackage] ?: 0L
            val sameTitle = lastPlaceholderTitles[ownerPackage]
                ?.let { normalizeTitle(it) == normalizeTitle(songTitle) } ?: false
            if (generation > 0L && generation <= lastGen && sameTitle) {
                return@post
            }
            if (!acceptGeneration(ownerPackage, generation, songTitle, sessionId)) return@post
            lastPlaceholderGenerations[ownerPackage] = maxOf(lastGen, generation)
            lastPlaceholderTitles[ownerPackage] = songTitle
            Log.i(TAG, "placeholder $ownerPackage gen=$generation title=$songTitle")
            val key = "placeholder|$songTitle|$songArtist|$duration"
            val metadata = LyricMetadata(mapOf("placeholder" to "true"))
            val song = Song(key, songTitle, songArtist, duration, metadata, null)
            publisherFor(ownerPackage).submit(key, song, playback, false, false)
        }
    }

    @JvmStatic
    fun updatePlayback(ownerPackage: String?, playback: PlaybackState?) {
        if (ownerPackage.isNullOrBlank() || playback == null) return
        worker.post { publishers[ownerPackage]?.updatePlayback(playback) }
    }

    @JvmStatic
    fun release(ownerPackage: String?) {
        if (ownerPackage.isNullOrBlank()) return
        worker.post {
            generations.remove(ownerPackage)
            lastPayloadKeys.remove(ownerPackage)
            lastPlaceholderGenerations.remove(ownerPackage)
            lastTitles.remove(ownerPackage)
            lastPlaceholderTitles.remove(ownerPackage)
            lastSessionIds.remove(ownerPackage)
            publishers.remove(ownerPackage)?.clear()
        }
    }

    
    fun payloadTitleMatches(payload: String, title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val payloadTitle = runCatching { JSONObject(payload).optString("songName") }.getOrDefault("")
        if (payloadTitle.isBlank()) return true
        val payloadKey = normalizeTitle(payloadTitle)
        val titleKey = normalizeTitle(title)
        if (payloadKey == titleKey) return true
        
        return payloadKey.length >= 3 && titleKey.length >= 3 &&
            (payloadKey.contains(titleKey) || titleKey.contains(payloadKey))
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase().replace(Regex("[\\s\\-–—_():：()（）\\[\\]【】·]"), "")

    private fun publisherFor(ownerPackage: String): LyriconPublisher =
        publishers.getOrPut(ownerPackage) { LyriconPublisher(worker, ownerPackage) }

    private fun acceptGeneration(
        ownerPackage: String,
        generation: Long,
        title: String?,
        sessionId: String?,
    ): Boolean {
        val previousSession = lastSessionIds[ownerPackage]
        if (!sessionId.isNullOrBlank() && previousSession != null && previousSession != sessionId) {
            
            generations.remove(ownerPackage)
            lastTitles.remove(ownerPackage)
            lastPayloadKeys.remove(ownerPackage)
            lastPlaceholderGenerations.remove(ownerPackage)
            lastPlaceholderTitles.remove(ownerPackage)
        }
        if (!sessionId.isNullOrBlank()) lastSessionIds[ownerPackage] = sessionId
        val last = generations[ownerPackage] ?: 0L
        if (generation > 0L && generation < last) {
            return false
        }
        generations[ownerPackage] = maxOf(last, generation)
        if (!title.isNullOrBlank()) lastTitles[ownerPackage] = title
        return true
    }

    




    private fun normalizePayload(payload: String): String = runCatching {
        val json = JSONObject(payload)
        val raw = json.optString("rawLyric")
        if (raw.isNotBlank() && !WordTimedLyricConverter.hasWordTiming(raw)) {
            WordTimedLyricConverter.convert(raw)?.let { json.put("rawLyric", it) }
        }
        val lyric = json.optString("lyric")
        if (lyric.isNotBlank()) {
            json.put("lyric", UniversalLyricPayloadPolicy.stripWordTiming(lyric))
        }
        json.toString()
    }.getOrDefault(payload)
}
