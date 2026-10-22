package io.github.cn_cizhanyue.lyricon.universal.provider.cache

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UniversalLyricCacheTest {
    private fun hit(title: String, artist: String) =
        UniversalLyricCache.EntryFields(title, artist, noLyric = false, hasLyric = true)

    private fun miss(title: String, artist: String) =
        UniversalLyricCache.EntryFields(title, artist, noLyric = true, hasLyric = false)

    @Test
    fun countsUniqueSongsAcrossLegacyKeys() {
        val stats = UniversalLyricCache.statsFromEntries(
            mapOf(
                "v7|fatal|gemn" to hit("Fatal", "GEMN"),
                "v9|fatal|gemn" to hit("Fatal", "GEMN"),
                "fatal|gemn" to hit("Fatal", "GEMN"),
                "v9|anti-hero|taylor swift" to hit("Anti-Hero", "Taylor Swift"),
                "v9|missing|nobody" to miss("Missing", "Nobody")
            )
        )
        assertEquals(2, stats.songs)
        assertEquals(1, stats.negative)
        assertEquals(5, stats.keys)
    }

    @Test
    fun aliasKeysCoverLegacyVersions() {
        val aliases = UniversalLyricCache.aliasKeys("Gold Rush", "Taylor Swift")
        assertTrue(aliases.contains("v9|gold rush|taylor swift"))
        assertTrue(aliases.contains("v7|gold rush|taylor swift"))
        assertTrue(aliases.contains("gold rush|taylor swift"))
        assertEquals("v9|gold rush|taylor swift", UniversalLyricCache.trackKey("Gold Rush", "Taylor Swift"))
    }

    @Test
    fun identityFromKeyUnderstandsVersionedAndPlainKeys() {
        assertEquals("fatal" to "gemn", UniversalLyricCache.identityFromKey("v9|fatal|gemn"))
        assertEquals("fatal" to "gemn", UniversalLyricCache.identityFromKey("fatal|gemn"))
    }
}

