package io.github.cn_cizhanyue.lyricon.universal.provider.cache

import org.json.JSONObject
import java.io.File
import android.content.Context

object UniversalLyricCache {
    private data class StatsSnapshot(
        val path: String,
        val modified: Long,
        val size: Long,
        val stats: Stats
    )
    private var statsSnapshot: StatsSnapshot? = null
    const val OWN_PATH = "/data/system/lyricon_lyric_cache.json"
    const val OFFICIAL_PATH = "/data/system/universal_lyric_cache.json"
    const val SYSTEM_PATH = OWN_PATH
    const val VERSION = "v9"
    private val VERSIONED_KEY = Regex("^v\\d+\\|")

    data class Stats(
        val songs: Int,
        val negative: Int,
        val keys: Int
    ) {
        val total: Int get() = songs + negative
    }

    fun trackKey(title: String, artist: String, version: String = VERSION): String =
        "$version|${normalize(title)}|${normalize(artist)}"

    @Synchronized
    fun get(title: String, artist: String, file: File = File(OWN_PATH)): String? =
        readValue(file, title, artist)

    @Synchronized
    fun getOwn(title: String, artist: String): String? = readValue(File(OWN_PATH), title, artist)

    @Synchronized
    fun getOfficial(title: String, artist: String): String? = readValue(File(OFFICIAL_PATH), title, artist)

    private fun readValue(file: File, title: String, artist: String): String? {
        val root = readRoot(file) ?: return null
        aliasKeys(title, artist).forEach { key ->
            val value = root.optString(key)
            if (value.isNotBlank()) return value
        }
        return null
    }

    @Synchronized
    fun put(title: String, artist: String, lyricInfoJson: String, file: File = File(OWN_PATH)) {
        // Invalidate even if a rewrite keeps both the file size and timestamp unchanged.
        statsSnapshot = null
        val root = readRoot(file) ?: JSONObject()
        aliasKeys(title, artist).forEach { key -> root.remove(key) }
        root.put(trackKey(title, artist), lyricInfoJson)
        writeRoot(file, root)
    }

    @Synchronized
    fun clear(file: File = File(OWN_PATH)): Stats {
        val previous = stats(file)
        statsSnapshot = null
        if (file.isFile) runCatching { file.delete() }
        return previous
    }

    @Synchronized
    fun stats(file: File = File(OWN_PATH)): Stats {
        val path = file.absolutePath
        val modified = file.lastModified()
        val size = file.length()
        if (file.isFile) {
            statsSnapshot?.takeIf { it.path == path && it.modified == modified && it.size == size }
                ?.let { return it.stats }
        }
        statsSnapshot = null
        val root = readRoot(file) ?: return Stats(0, 0, 0)
        val entries = LinkedHashMap<String, EntryFields>()
        root.keys().forEach { key ->
            val raw = root.optString(key)
            if (raw.isBlank()) return@forEach
            val payload = runCatching { JSONObject(raw) }.getOrNull()
            entries[key] = EntryFields(
                songName = payload?.optString("songName").orEmpty(),
                artist = payload?.optString("artist").orEmpty(),
                noLyric = payload?.optBoolean("noLyric", false) == true,
                hasLyric = payload != null &&
                    (payload.optString("lyric").isNotBlank() || payload.optString("rawLyric").isNotBlank())
            )
        }
        return statsFromEntries(entries).also {
            statsSnapshot = StatsSnapshot(path, modified, size, it)
        }
    }

    fun getCacheCount(file: File = File(OWN_PATH)): Int = stats(file).songs

    fun get(context: Context, title: String, artist: String): String? = get(title, artist)

    fun put(context: Context, title: String, artist: String, lyricInfoJson: String) {
        put(title, artist, lyricInfoJson)
    }

    fun getCacheCount(context: Context): Int = getCacheCount()

    fun clearCache(context: Context): Int = clear().songs

    fun putManualBinding(context: Context, title: String, artist: String, lyricInfoJson: String) {
        context.getSharedPreferences("lyricon_manual_bindings", Context.MODE_PRIVATE)
            .edit()
            .putString("${normalize(title)}|${normalize(artist)}", lyricInfoJson)
            .apply()
    }

    internal data class EntryFields(
        val songName: String,
        val artist: String,
        val noLyric: Boolean,
        val hasLyric: Boolean
    )

    internal fun statsFromEntries(entries: Map<String, EntryFields>): Stats {
        val identities = LinkedHashMap<String, Boolean>()
        entries.forEach { (key, payload) ->
            val identity = identityOf(key, payload) ?: return@forEach
            val previous = identities[identity]
            if (previous == true) return@forEach
            if (!payload.noLyric && payload.hasLyric) {
                identities[identity] = true
            } else if (payload.noLyric && previous == null) {
                identities[identity] = false
            }
        }
        return Stats(
            songs = identities.values.count { it },
            negative = identities.values.count { !it },
            keys = entries.size
        )
    }

    private fun identityOf(key: String, payload: EntryFields): String? {
        if (payload.songName.isNotBlank()) {
            return normalize(payload.songName) + "|" + normalize(payload.artist)
        }
        val fromKey = identityFromKey(key) ?: return null
        return fromKey.first + "|" + fromKey.second
    }

    internal fun identityFromKey(key: String): Pair<String, String>? {
        val parts = key.split('|')
        return when {
            parts.size >= 3 && parts[0].matches(Regex("v\\d+")) ->
                parts[1] to parts.drop(2).joinToString("|")
            parts.size >= 2 && !VERSIONED_KEY.containsMatchIn(key) ->
                parts[0] to parts.drop(1).joinToString("|")
            else -> null
        }
    }

    internal fun aliasKeys(title: String, artist: String): List<String> {
        val versions = listOf(VERSION, "v7", "v6", "v5", "v4")
        return versions.map { trackKey(title, artist, it) } +
            "${normalize(title)}|${normalize(artist)}"
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    private fun readRoot(file: File): JSONObject? {
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun writeRoot(file: File, root: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }
}
