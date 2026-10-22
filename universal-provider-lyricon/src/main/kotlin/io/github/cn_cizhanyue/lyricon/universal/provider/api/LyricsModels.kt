package io.github.cn_cizhanyue.lyricon.universal.provider.api

data class TrackQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val generation: Long = 0L
)

data class LyricResult(
    val title: String,
    val artist: String,
    val lyric: String,
    val rawLyric: String? = null,
    val translationLyric: String? = null,
    val source: String,
    val sourceTrackId: String? = null
)

data class SearchCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val source: String,
    val coverUrl: String = "",
    val durationMs: Long = 0L
)

data class FetchReport(
    val result: LyricResult?,
    val transientFailure: Boolean,
    val attemptCount: Int,
    val reason: String
)

interface LyricsSource {
    val name: String
    fun fetchLyrics(query: TrackQuery): LyricResult?
    fun searchCandidates(query: String): List<SearchCandidate> = emptyList()
    fun fetchLyricByCandidate(candidate: SearchCandidate): LyricResult? = null
}
