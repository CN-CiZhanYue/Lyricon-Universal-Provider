package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalDiagnostics
import org.json.JSONArray
import java.io.IOException
import io.github.andrealtb.coloroslyrics.provider.parser.yrc.YrcParser

class NetEaseSource(private val client: OkHttpClient) : LyricsSource {
    override val name: String = "netease"

    override fun fetchLyrics(query: TrackQuery): LyricResult? {
        val songId = search(query) ?: return null
        return fetch(songId, query)
    }

    private fun search(query: TrackQuery): Long? {
        val threshold = 0.45
        val attempts = listOf(
            "title+artist" to "${query.title} ${query.artist}".trim(),
            "title" to query.title.trim()
        )
        var bestId: Long? = null
        var bestScore = -1.0
        var bestCount = 0
        var bestMode = "title+artist"
        for ((mode, keyword) in attempts) {
            if (keyword.isBlank()) continue
            val songs = searchSongs(query.generation, keyword, mode) ?: continue
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val score = SongMatchScorer.calculate(
                    query.title,
                    query.artist,
                    query.durationMs,
                    NetEaseSearchResponseParser.title(song),
                    NetEaseSearchResponseParser.artist(song),
                    NetEaseSearchResponseParser.durationMs(song).takeIf { it > 0L }
                )
                if (score > bestScore) {
                    bestScore = score
                    bestId = NetEaseSearchResponseParser.songId(song)
                    bestCount = songs.length()
                    bestMode = mode
                }
            }
            if (bestScore >= threshold) break
        }
        UniversalDiagnostics.sourceMatch(
            source = name,
            generation = query.generation,
            candidateCount = bestCount,
            bestScore = bestScore,
            threshold = threshold,
            accepted = bestScore >= threshold && bestId != null && bestId != 0L,
            keywordMode = bestMode,
            durationPresent = query.durationMs != null && query.durationMs > 0L
        )
        return if (bestScore >= threshold) bestId else null
    }

    private fun searchSongs(generation: Long, keyword: String, mode: String): JSONArray? {
        val endpoints = listOf(
            "cloudsearch" to "https://music.163.com/api/cloudsearch/pc",
            "search-get" to "https://music.163.com/api/search/get/web"
        )
        var lastError: IOException? = null
        for ((op, url) in endpoints) {
            val formBody = FormBody.Builder()
                .add("s", keyword)
                .add("type", "1")
                .add("offset", "0")
                .add("limit", "10")
                .add("total", "true")
                .build()
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Referer", "https://music.163.com/")
                .addHeader("Cookie", "os=pc; appver=8.9.70")
                .build()
            val started = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = if (body.isBlank()) JSONObject() else JSONObject(body)
                    val songs = NetEaseSearchResponseParser.songs(json)
                    UniversalDiagnostics.networkCall(
                        source = name,
                        op = op + "-" + mode,
                        generation = generation,
                        httpCode = response.code,
                        durationMs = System.currentTimeMillis() - started,
                        bodyChars = body.length,
                        parseOk = response.isSuccessful && songs.length() > 0,
                        extra = "songs=" + songs.length()
                    )
                    if (!response.isSuccessful) {
                        lastError = IOException("netease " + op + " http=" + response.code)
                    } else if (songs.length() > 0) {
                        return songs
                    }
                }
            } catch (error: IOException) {
                lastError = error
                UniversalDiagnostics.sourceFailed(name, generation, error.javaClass.simpleName, System.currentTimeMillis() - started)
            }
        }
        lastError?.let { throw it }
        return JSONArray()
    }

    private fun fetch(songId: Long, query: TrackQuery): LyricResult? {
        val started = System.currentTimeMillis()
        var json = runCatching { NetEaseEapi.fetchLyric(client, songId) }.getOrNull()
        UniversalDiagnostics.networkCall(
            source = name,
            op = "lyric-eapi",
            generation = query.generation,
            httpCode = if (json != null) 200 else 0,
            durationMs = System.currentTimeMillis() - started,
            bodyChars = json?.toString()?.length ?: 0,
            parseOk = json != null && json.optJSONObject("yrc") != null
        )
        if (json == null || json.optJSONObject("yrc")?.optString("lyric").isNullOrBlank()) {
        val url = "https://music.163.com/api/song/lyric/v1".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("cp", "false")
            .addQueryParameter("lv", "-1")
            .addQueryParameter("kv", "-1")
            .addQueryParameter("tv", "-1")
            .addQueryParameter("rv", "-1")
            .addQueryParameter("yv", "-1")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .addHeader("Referer", "https://music.163.com/")
            .addHeader("Cookie", "os=pc; appver=8.9.70")
            .build()

            json = client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            UniversalDiagnostics.networkCall(
                source = name,
                op = "lyric-public",
                generation = query.generation,
                httpCode = response.code,
                durationMs = System.currentTimeMillis() - started,
                bodyChars = body.length,
                parseOk = response.isSuccessful && body.isNotBlank()
            )
            if (!response.isSuccessful) throw IOException("netease lyric http=" + response.code)
            if (body.isBlank()) return null
            JSONObject(body)
        }
        }

        val lyricJson = json ?: return null
        val lrc = lyricJson.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val yrc = lyricJson.optJSONObject("yrc")?.optString("lyric").orEmpty()
        val original = parseNeteaseOriginal(yrc, lrc)
        if (original.isEmpty()) {
            UniversalDiagnostics.sourceEmpty(name, query.generation, 0L, "structuredEmpty yrcChars=" + yrc.length + " lrcChars=" + lrc.length)
            return null
        }

        val isChineseSystem = Locale.getDefault().language == "zh"
        val tlyric = lyricJson.optJSONObject("tlyric")?.optString("lyric").orEmpty()
        val transLines = StructuredLyrics.parseLrc(tlyric)
        val merged = if (isChineseSystem) StructuredLyrics.merge(original, transLines) else emptyList()
        val lineLyric = StructuredLyrics.toPlainLrc(original)
        val raw = StructuredLyrics.toEnhancedLrc(original)
        val translation = StructuredLyrics.toTranslationLrc(merged).takeIf { it.isNotBlank() }
        UniversalDiagnostics.lyricPayload(
            source = name,
            generation = query.generation,
            lyricChars = lineLyric.length,
            rawChars = raw.length,
            hasWordTiming = StructuredLyrics.hasWordTiming(original),
            hasTranslation = !translation.isNullOrBlank(),
            converted = StructuredLyrics.hasWordTiming(original)
        )

        return LyricResult(
            title = query.title,
            artist = query.artist,
            lyric = lineLyric,
            rawLyric = raw,
            translationLyric = translation,
            source = "universal/netease",
            sourceTrackId = songId.toString()
        )
    }

    override fun searchCandidates(query: String): List<SearchCandidate> {
        return runCatching {
            val songs = searchSongs(0L, query.trim(), "manual") ?: return emptyList()
            val list = mutableListOf<SearchCandidate>()
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = NetEaseSearchResponseParser.songId(song)
                val name = NetEaseSearchResponseParser.title(song)
                val artist = NetEaseSearchResponseParser.artist(song)
                val album = NetEaseSearchResponseParser.album(song)
                if (id != 0L) {
                    list.add(
                        SearchCandidate(
                            id = id.toString(),
                            title = name,
                            artist = artist,
                            album = album,
                            source = "网易云音乐",
                            coverUrl = NetEaseSearchResponseParser.coverUrl(song),
                            durationMs = NetEaseSearchResponseParser.durationMs(song)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun convertYrcToEnhancedLrc(value: String): String? {
        return StructuredLyrics.toEnhancedLrc(parseNeteaseOriginal(value, "")).takeIf { it.isNotBlank() }
    }

    private fun parseNeteaseOriginal(yrc: String, lrc: String): List<TimedLine> {
        val fromParser = YrcParser.parse(yrc).map { line ->
            val words = line.words.orEmpty().map { TimedWord(it.begin, it.end, it.text.orEmpty()) }
            val safeWords = words.filter { it.text.isNotEmpty() }
            TimedLine(
                line.begin,
                line.end,
                if (safeWords.isNotEmpty()) safeWords else listOf(TimedWord(line.begin, line.end, line.text.orEmpty()))
            )
        }
        val fromJson = parseJsonYrc(yrc)
        val combined = (fromParser + fromJson).sortedBy { it.startMs }
        if (combined.isNotEmpty()) return combined
        return StructuredLyrics.parseLrc(lrc)
    }

    private fun parseJsonYrc(yrc: String): List<TimedLine> {
        if (yrc.isBlank()) return emptyList()
        val lines = mutableListOf<TimedLine>()
        yrc.lineSequence().forEach { raw ->
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{")) return@forEach
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return@forEach
            val start = obj.optLong("t")
            val parts = obj.optJSONArray("c") ?: return@forEach
            val text = buildString {
                for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("tx").orEmpty())
                }
            }.trim()
            if (text.isBlank()) return@forEach
            lines += TimedLine(start, start + 3000, listOf(TimedWord(start, start + 3000, text)))
        }
        return lines
    }

    private fun convertYrcToEnhancedLrcLegacy(value: String): String? {
        if (value.isBlank()) return null
        val lines = Regex("\\[(\\d+)\\s*,\\s*(\\d+)]").findAll(value).toList()
        return lines.mapIndexedNotNull { index, line ->
            val begin = line.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val duration = line.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
            val bodyStart = line.range.last + 1
            val bodyEnd = lines.getOrNull(index + 1)?.range?.first ?: value.length
            val body = value.substring(bodyStart, bodyEnd).trimEnd('\r', '\n')
            val words = Regex("\\((\\d+)\\s*,\\s*(\\d+)\\s*,\\s*\\d+\\)([^()]*)").findAll(body).toList()
            if (words.isEmpty()) return@mapIndexedNotNull null
            buildString {
                append('[').append(formatMs(begin)).append(']')
                words.forEach { word ->
                    val wordBegin = word.groupValues[1].toLongOrNull() ?: begin
                    append('<').append(formatMs(wordBegin)).append('>').append(word.groupValues[3])
                }
                append('<').append(formatMs(begin + duration)).append('>')
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun formatMs(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, (ms % 60_000) / 1_000, ms % 1_000
    )

    override fun fetchLyricByCandidate(candidate: SearchCandidate): LyricResult? {
        val id = candidate.id.toLongOrNull() ?: return null
        return fetch(id, TrackQuery(candidate.title, candidate.artist, candidate.album))
    }
}
