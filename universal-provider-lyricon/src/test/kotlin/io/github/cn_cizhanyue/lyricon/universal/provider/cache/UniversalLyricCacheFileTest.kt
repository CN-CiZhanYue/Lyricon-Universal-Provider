package io.github.cn_cizhanyue.lyricon.universal.provider.cache

import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UniversalLyricCacheFileTest {
    @get:Rule val folder = TemporaryFolder()

    private fun payload(title: String, noLyric: Boolean = false) = JSONObject()
        .put("songName", title)
        .put("artist", "artist")
        .put("noLyric", noLyric)
        .put("lyric", if (noLyric) "" else "[00:01.00]test")
        .toString()

    @Test
    fun replacingCachedSongWithNoLyricUpdatesStats() {
        val file = folder.newFile()
        UniversalLyricCache.put("one", "artist", payload("one"), file)
        assertEquals(1, UniversalLyricCache.getCacheCount(file))
        assertEquals(1, UniversalLyricCache.getCacheCount(file))
        UniversalLyricCache.put("one", "artist", payload("one", noLyric = true), file)
        assertEquals(UniversalLyricCache.Stats(0, 1, 1), UniversalLyricCache.stats(file))
    }

    @Test
    fun clearingAndRecreatingDoesNotReuseOldCount() {
        val file = folder.newFile()
        UniversalLyricCache.put("one", "artist", payload("one"), file)
        UniversalLyricCache.put("two", "artist", payload("two"), file)
        assertEquals(2, UniversalLyricCache.getCacheCount(file))
        assertEquals(2, UniversalLyricCache.clear(file).songs)
        assertEquals(0, UniversalLyricCache.getCacheCount(file))
        UniversalLyricCache.put("three", "artist", payload("three"), file)
        assertEquals(1, UniversalLyricCache.getCacheCount(file))
    }

    @Test
    fun externalFileReplacementInvalidatesStats() {
        val file = folder.newFile()
        UniversalLyricCache.put("one", "artist", payload("one"), file)
        assertEquals(1, UniversalLyricCache.getCacheCount(file))
        file.writeText(JSONObject()
            .put("v9|one|artist", payload("one"))
            .put("v9|another song|artist", payload("another song")).toString())
        assertEquals(2, UniversalLyricCache.getCacheCount(file))
    }

    @Test
    fun concurrentWritesKeepEveryBindingAndAccurateStats() {
        val file = folder.newFile()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 12).map { index -> Callable {
                val title = "track-" + index
                UniversalLyricCache.put(title, "artist", payload(title), file)
                UniversalLyricCache.getCacheCount(file)
            } }
            executor.invokeAll(tasks).forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(12, UniversalLyricCache.getCacheCount(file))
            (0 until 12).forEach {
                assertNotNull(UniversalLyricCache.get("track-" + it, "artist", file))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
