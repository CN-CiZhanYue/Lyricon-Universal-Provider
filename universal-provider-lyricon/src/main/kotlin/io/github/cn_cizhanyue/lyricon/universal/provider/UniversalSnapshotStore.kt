package io.github.cn_cizhanyue.lyricon.universal.provider

import android.util.AtomicFile
import java.io.File

internal object UniversalSnapshotStore {
    const val ACTION_SNAPSHOT_UPDATE = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_SNAPSHOT_UPDATE"
    const val ACTION_REQUEST_SNAPSHOT = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_REQUEST_SNAPSHOT"
    const val ACTION_REQUEST_PLAYER_BINDINGS_SYNC = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_REQUEST_PLAYER_BINDINGS_SYNC"
    const val ACTION_UPDATE_PLAYER_BINDINGS = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_UPDATE_PLAYER_BINDINGS"
    const val ACTION_INJECT_REAL_LYRIC = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_INJECT_REAL_LYRIC"
    const val ACTION_CLEAR_CACHE = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_CLEAR_CACHE"
    const val ACTION_UPDATE_SOURCE_CONFIG = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_UPDATE_SOURCE_CONFIG"
    const val EXTRA_CACHED_SONGS = "extra_cached_songs"
    const val EXTRA_CACHE_CLEARED = "extra_cache_cleared"
    const val EXTRA_SOURCE_PRIORITY = "extra_source_priority"
    const val EXTRA_WORD_TIMING_ENABLED = "extra_word_timing_enabled"
    const val EXTRA_TRANSLATION_ENABLED = "extra_translation_enabled"
    const val EXTRA_DEBUG_ENABLED = "extra_debug_enabled"
    const val EXTRA_CLOCK_LINE_FALLBACK_ENABLED = "extra_clock_line_fallback_enabled"
    const val EXTRA_NETWORK_ENABLED = "extra_network_enabled"
    const val ACTION_UPDATE_AI_CONFIG = "io.github.cn_cizhanyue.lyricon.universal.provider.ACTION_UPDATE_AI_CONFIG"
    const val EXTRA_AI_CONFIG = "extra_ai_config"
    const val EXTRA_BOUND_PACKAGES = "extra_bound_packages"
    const val EXTRA_OWNER_PACKAGE = "extra_owner_package"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_LYRIC_INFO = "extra_lyric_info"
    const val EXTRA_SNAPSHOT = "extra_snapshot"
    const val EXTRA_ARTWORK = "extra_artwork"
    const val EXTRA_ARTWORK_REVISION = "extra_artwork_revision"
    private const val PATH = "/data/user/0/io.github.cn_cizhanyue.lyricon.universal.provider/files/p1-snapshot.txt"

    fun file(): File = File(PATH)

    private var lastWrittenText: String? = null

    fun write(text: String) {
        val target = file()
        if (text == lastWrittenText && target.isFile) return
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            lastWrittenText = text
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    fun read(): String? = runCatching {
        AtomicFile(file()).readFully().toString(Charsets.UTF_8)
    }.getOrNull()
}
