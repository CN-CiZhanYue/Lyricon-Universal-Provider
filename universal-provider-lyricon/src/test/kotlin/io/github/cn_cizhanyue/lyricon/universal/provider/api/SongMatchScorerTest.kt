package io.github.cn_cizhanyue.lyricon.universal.provider.api

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongMatchScorerTest {
    @Test
    fun keepsTimedLyrics() {
        val lrc = """
            [ti:Anti-Hero]
            [00:15.20]I have this dream
            [00:18.10]You're a sexy baby
            [00:21.00]And I'm a monster on the hill
        """.trimIndent()
        assertFalse(SongMatchScorer.isInstrumentalOrCreditsOnly(lrc))
    }

    @Test
    fun rejectsCreditsOnly() {
        val lrc = """
            [00:00.00]作词 : Taylor Swift
            [00:01.00]作曲 : Taylor Swift
            [00:02.00]纯音乐，请欣赏
        """.trimIndent()
        assertTrue(SongMatchScorer.isInstrumentalOrCreditsOnly(lrc))
    }
}
