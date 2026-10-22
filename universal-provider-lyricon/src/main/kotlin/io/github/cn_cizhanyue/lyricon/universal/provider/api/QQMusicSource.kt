package io.github.cn_cizhanyue.lyricon.universal.provider.api

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import io.github.andrealtb.coloroslyrics.provider.parser.qrc.decrypt.QrcDecrypter
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalDiagnostics
import org.json.JSONArray
import java.io.IOException

class QQMusicSource(private val client: OkHttpClient) : LyricsSource {
    override val name: String = "qq"

    override fun fetchLyrics(query: TrackQuery): LyricResult? {
        val song = search(query) ?: return null
        val songMid = QqSearchResponseParser.mid(song)
        val songId = QqSearchResponseParser.songId(song)
        if (songMid.isBlank()) return null
        return fetch(songMid, songId, query)
    }

    private fun search(query: TrackQuery): JSONObject? {
        val threshold = 0.45
        val attempts = listOf(
            "title+artist" to "${query.title} ${query.artist}".trim(),
            "title" to query.title.trim()
        )
        var bestMatch: JSONObject? = null
        var bestScore = -1.0
        var bestCount = 0
        var bestMode = "title+artist"
        for ((mode, keyword) in attempts) {
            if (keyword.isBlank()) continue
            val songs = searchSongs(query.generation, keyword, mode) ?: continue
            var localBest: JSONObject? = null
            var localScore = -1.0
            for (i in 0 until songs.length()) {
                val item = songs.optJSONObject(i) ?: continue
                val score = SongMatchScorer.calculate(
                    query.title,
                    query.artist,
                    query.durationMs,
                    QqSearchResponseParser.title(item),
                    QqSearchResponseParser.artist(item),
                    QqSearchResponseParser.durationMs(item).takeIf { it > 0L }
                )
                if (score > localScore) {
                    localScore = score
                    localBest = item
                }
            }
            if (localScore > bestScore) {
                bestScore = localScore
                bestMatch = localBest
                bestCount = songs.length()
                bestMode = mode
            }
            if (bestScore >= threshold) break
        }
        UniversalDiagnostics.sourceMatch(
            source = name,
            generation = query.generation,
            candidateCount = bestCount,
            bestScore = bestScore,
            threshold = threshold,
            accepted = bestScore >= threshold,
            keywordMode = bestMode,
            durationPresent = query.durationMs != null && query.durationMs > 0L
        )
        return if (bestScore >= threshold) bestMatch else null
    }

    private fun searchSongs(generation: Long, keyword: String, mode: String): JSONArray? {
        val payload = JSONObject().apply {
            put("comm", qqComm())
            put("req_0", JSONObject().apply {
                put("module", "music.search.SearchCgiService")
                put("method", "DoSearchForQQMusicLite")
                put("param", JSONObject().apply {
                    put("search_id", System.currentTimeMillis().toString())
                    put("remoteplace", "search.android.keyboard")
                    put("query", keyword)
                    put("search_type", 0)
                    put("num_per_page", 10)
                    put("page_num", 1)
                    put("highlight", 0)
                    put("nqc_flag", 0)
                    put("page_id", 1)
                    put("grp", 1)
                })
            })
        }
        val json = postJson(generation, "search-" + mode, payload) ?: return null
        val songs = QqSearchResponseParser.songArray(json)
        if (songs.length() > 0) return songs
        val reqCode = json.optJSONObject("req")?.optInt("code", -1) ?: -1
        UniversalDiagnostics.networkCall(
            source = name,
            op = "search-" + mode + "-empty",
            generation = generation,
            httpCode = 200,
            durationMs = 0L,
            bodyChars = json.toString().length,
            parseOk = false,
            extra = "reqCode=" + reqCode + " fallback=lite"
        )
        return searchSongsLite(generation, keyword, mode)
    }

    private fun searchSongsLite(generation: Long, keyword: String, mode: String): JSONArray? {
        val payload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "19")
                put("cv", "1859")
                put("uin", "0")
            })
            put("req", JSONObject().apply {
                put("module", "music.search.SearchCgiService")
                put("method", "DoSearchForQQMusicLite")
                put("param", JSONObject().apply {
                    put("search_type", 0)
                    put("query", keyword)
                    put("page_num", 1)
                    put("num_per_page", 10)
                })
            })
        }
        val encodedData = java.net.URLEncoder.encode(payload.toString(), "UTF-8")
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + encodedData)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .addHeader("Referer", "https://y.qq.com/")
            .build()
        val started = System.currentTimeMillis()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = if (body.isBlank()) JSONObject() else JSONObject(body)
            val songs = QqSearchResponseParser.songArray(json)
            UniversalDiagnostics.networkCall(
                source = name,
                op = "search-lite-" + mode,
                generation = generation,
                httpCode = response.code,
                durationMs = System.currentTimeMillis() - started,
                bodyChars = body.length,
                parseOk = response.isSuccessful && songs.length() > 0,
                extra = "songs=" + songs.length()
            )
            if (!response.isSuccessful) throw IOException("qq lite http=" + response.code)
            return songs
        }
    }

    private fun fetch(songMid: String, songId: Long, query: TrackQuery): LyricResult? {
        val payload = JSONObject().apply {
            put("comm", qqComm())
            put("req_0", JSONObject().apply {
                put("module", "music.musichallSong.PlayLyricInfo")
                put("method", "GetPlayLyricInfo")
                put("param", JSONObject().apply {
                    put("songMID", songMid)
                    put("songID", songId)
                    put("songName", android.util.Base64.encodeToString(query.title.toByteArray(), android.util.Base64.NO_WRAP))
                    put("crypt", 1)
                    put("qrc", 1)
                    put("roma", 0)
                    put("trans", 1)
                    put("cv", 2111)
                    put("ct", 19)
                    put("lrc_t", 0)
                    put("qrc_t", 0)
                    put("roma_t", 0)
                    put("trans_t", 0)
                    put("type", 0)
                })
            })
        }

        val json = postJson(query.generation, "lyric", payload) ?: return null
        val data = json.optJSONObject("req_0")?.optJSONObject("data")
            ?: json.optJSONObject("req")?.optJSONObject("data")
        if (data == null) {
            UniversalDiagnostics.sourceEmpty(name, query.generation, 0L, "lyricDataMissing")
            return null
        }

        val qrcText = decodeQqLyricPayload(data.optString("lyric").ifBlank { data.optString("qrc") })
        val transText = decodeQqLyricPayload(data.optString("trans"))
        val original = StructuredLyrics.parseQrc(qrcText).ifEmpty { StructuredLyrics.parseLrc(qrcText) }
        if (original.isEmpty()) {
            UniversalDiagnostics.sourceEmpty(name, query.generation, 0L, "structuredEmpty qrcChars=" + qrcText.length)
            return null
        }

        val isChineseSystem = Locale.getDefault().language == "zh"
        val transLines = StructuredLyrics.parseLrc(transText).ifEmpty { StructuredLyrics.parseQrc(transText) }
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
            source = "universal/qq",
            sourceTrackId = songMid
        )
    }

    private fun postJson(generation: Long, op: String, payload: JSONObject): JSONObject? {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .post(payload.toString().toRequestBody(mediaType))
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")
            .addHeader("Referer", "https://y.qq.com/n/ryqq/search")
            .addHeader("Origin", "https://y.qq.com")
            .addHeader("Accept", "application/json, text/plain, */*")
            .build()
        val started = System.currentTimeMillis()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            UniversalDiagnostics.networkCall(
                source = name,
                op = op,
                generation = generation,
                httpCode = response.code,
                durationMs = System.currentTimeMillis() - started,
                bodyChars = body.length,
                parseOk = response.isSuccessful && body.isNotBlank()
            )
            if (!response.isSuccessful) {
                throw IOException("qq " + op + " http=" + response.code)
            }
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun decodeBase64OrSelf(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun qqComm(): JSONObject = JSONObject().apply {
        put("ct", "11")
        put("cv", "1003006")
        put("v", "1003006")
        put("os_ver", "15")
        put("phonetype", "24122RKC7C")
        put("tmeAppID", "qqmusiclight")
        put("nettype", "NETWORK_WIFI")
    }

    private fun isHexPayload(value: String): Boolean {
        if (value.length < 32 || value.length % 2 != 0) return false
        return value.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' }
    }

    private fun decodeQqLyricPayload(raw: String): String {
        if (raw.isBlank()) return ""
        if (isHexPayload(raw)) {
            return QrcDecrypter.decrypt(raw)?.takeIf { it.isNotBlank() } ?: raw
        }
        val decoded = decodeBase64OrSelf(raw)
        if (isHexPayload(decoded)) {
            return QrcDecrypter.decrypt(decoded)?.takeIf { it.isNotBlank() } ?: decoded
        }
        return decoded
    }

    private fun toPlainOrRaw(value: String): String {
        if (value.isBlank()) return ""
        if (value.contains("LyricContent") || value.contains("<Lyric")) return ""
        return value.replace(WordTimedLyricConverter.WORD_STAMP, "")
    }

    private fun decryptAndConvertQrc(value: String): String? {
        if (value.isBlank()) return null
        val trimmed = value.trim()
        val encrypted = if (trimmed.length % 2 == 0 && trimmed.matches(Regex("[0-9A-Fa-f]+"))) {
            trimmed
        } else {
            decodeBase64OrSelf(trimmed).trim()
        }
        val xml = QrcDecrypter.decrypt(encrypted) ?: return null
        val content = Regex("LyricContent\\s*=\\s*\"([\\s\\S]*?)\"(?=\\s*/?>)")
            .find(xml)?.groupValues?.getOrNull(1) ?: return null
        val decoded = content.replace("&quot;", "\"").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'")
        val lines = Regex("\\[(\\d+)\\s*,\\s*(\\d+)]").findAll(decoded).toList()
        return lines.mapIndexedNotNull { index, line ->
            val begin = line.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val duration = line.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
            val bodyStart = line.range.last + 1
            val bodyEnd = lines.getOrNull(index + 1)?.range?.first ?: decoded.length
            val body = decoded.substring(bodyStart, bodyEnd).trimEnd('\r', '\n')
            val words = Regex("([^()\\r\\n]*?)\\((\\d+)\\s*,\\s*(\\d+)\\)").findAll(body).toList()
            if (words.isEmpty()) return@mapIndexedNotNull null
            buildString {
                append('[').append(formatMs(begin)).append(']')
                words.forEach { word ->
                    val wordBegin = word.groupValues[2].toLongOrNull() ?: begin
                    append('<').append(formatMs(wordBegin)).append('>').append(word.groupValues[1])
                }
                append('<').append(formatMs(begin + duration)).append('>')
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun formatMs(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, (ms % 60_000) / 1_000, ms % 1_000
    )

    override fun searchCandidates(query: String): List<SearchCandidate> {
        return runCatching {
            val items = searchSongs(0L, query.trim(), "manual") ?: return emptyList()
            val list = mutableListOf<SearchCandidate>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val mid = QqSearchResponseParser.mid(item)
                val title = QqSearchResponseParser.title(item)
                val singer = QqSearchResponseParser.artist(item)
                val album = QqSearchResponseParser.album(item)
                if (mid.isNotBlank()) {
                    list.add(
                        SearchCandidate(
                            id = mid,
                            title = title,
                            artist = singer,
                            album = album,
                            source = "QQ音乐",
                            coverUrl = QqSearchResponseParser.coverUrl(item),
                            durationMs = QqSearchResponseParser.durationMs(item)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    override fun fetchLyricByCandidate(candidate: SearchCandidate): LyricResult? {
        return fetch(candidate.id, 0L, TrackQuery(candidate.title, candidate.artist, candidate.album))
    }
}
