package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalDiagnostics

class LyricsSourceAggregator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        // Accessed by app IO jobs, so entering a screen never initializes the network stack.
        val shared: LyricsSourceAggregator by lazy { LyricsSourceAggregator() }
    }

    enum class SourceId {
        QQ, NETEASE,
        APPLE_MUSIC,
        SHARED_CACHE
    }

    val qqSource = QQMusicSource(client)
    val netEaseSource = NetEaseSource(client)
    val appleMusicSource = AppleMusicLyricSource(client)
    val sharedCacheSource = SharedCacheSource()

    fun fetchFirst(query: TrackQuery, priority: List<SourceId> = SourceId.values().toList()): FetchReport {
        if (priority.isEmpty()) {
            return FetchReport(result = null, transientFailure = false, attemptCount = 0, reason = "priorityEmpty")
        }
        val ordered = priority.distinct()
        val priorityText = ordered.joinToString(",") { it.name }
        var transientFailure = false
        val reasons = mutableListOf<String>()
        var best: LyricResult? = null
        var bestScore = -1
        for (id in priority.distinct()) {
            val started = System.currentTimeMillis()
            UniversalDiagnostics.sourceAttempt(id.name, query.generation, priorityText)
            val attempt = runCatching {
                when (id) {
                    SourceId.QQ -> qqSource.fetchLyrics(query)
                    SourceId.NETEASE -> netEaseSource.fetchLyrics(query)
                    SourceId.APPLE_MUSIC -> appleMusicSource.fetchLyrics(query)
                    SourceId.SHARED_CACHE -> sharedCacheSource.fetchLyrics(query)
                }
            }
            val elapsed = System.currentTimeMillis() - started
            val result = attempt.getOrNull()
            if (attempt.isFailure) {
                transientFailure = true
                val exceptionName = attempt.exceptionOrNull()?.javaClass?.simpleName ?: "Unknown"
                UniversalDiagnostics.sourceFailed(id.name, query.generation, exceptionName, elapsed)
                reasons += id.name + ":exception:" + exceptionName
            } else if (result == null || result.lyric.isBlank()) {
                UniversalDiagnostics.sourceEmpty(id.name, query.generation, elapsed, "blankOrUnmatched")
                reasons += id.name + ":empty"
            }
            if (result != null && result.lyric.isNotBlank()) {
                val score = qualityScore(result)
                reasons += id.name + ":hit:" + score
                if (score > bestScore) {
                    best = result
                    bestScore = score
                }
                if (score >= 100) {
                    return FetchReport(result = result, transientFailure = false, attemptCount = reasons.size, reason = "hit:" + id.name + ":wordTimed")
                }
            }
        }
        return FetchReport(
            result = best,
            transientFailure = if (best != null) false else transientFailure,
            attemptCount = reasons.size,
            reason = if (best != null) "fallback:" + best.source else if (reasons.isEmpty()) "noSources" else reasons.joinToString(";")
        )
    }

    private fun qualityScore(result: LyricResult): Int {
        var score = 1
        if (WordTimedLyricConverter.hasRealWordTiming(result.rawLyric ?: result.lyric)) score += 100
        if (!result.translationLyric.isNullOrBlank()) score += 10
        return score
    }
}
