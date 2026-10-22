package io.github.cn_cizhanyue.lyricon.universal.provider.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredLyricsTest {
    @Test
    fun parsesQrcXmlAndMergesTranslationByWindow() {
        val qrc = "<Lyric_1 LyricType=\"1\" LyricContent=\"[1000,800]Hel(1000,300)lo(1300,500)\"/>"
        val original = StructuredLyrics.parseQrc(qrc)
        assertEquals(1, original.size)
        assertTrue(StructuredLyrics.hasWordTiming(original))
        val second = TimedLine(2000, 2700, listOf(TimedWord(2000, 2700, "World")))
        val lines = original + second
        val trans = listOf(
            TimedLine(1050, 1800, listOf(TimedWord(1050, 1800, "你好"))),
            TimedLine(2080, 2700, listOf(TimedWord(2080, 2700, "世界")))
        )
        val merged = StructuredLyrics.merge(lines, trans)
        val lrc = StructuredLyrics.toTranslationLrc(merged)
        assertTrue(lrc.contains("[00:01.000]你好"))
        assertTrue(lrc.contains("[00:02.000]世界"))
    }

    @Test
    fun doesNotBindFirstTranslationToCreditLines() {
        val original = listOf(
            TimedLine(0, 3000, listOf(TimedWord(0, 3000, "Composed by: Andrew"))),
            TimedLine(3000, 9000, listOf(TimedWord(3000, 9000, "Produced by: Andrew Watt/Louis Bell"))),
            TimedLine(9000, 12000, listOf(TimedWord(9000, 12000, "I had a bad week"))),
            TimedLine(12000, 16000, listOf(TimedWord(12000, 16000, "Spent the evening pretending it wasn't")))
        )
        val trans = listOf(
            TimedLine(9050, 12000, listOf(TimedWord(9050, 12000, "我这一周过得糟糕透顶"))),
            TimedLine(12080, 16000, listOf(TimedWord(12080, 16000, "整夜自我安慰 佯装也没那么苦")))
        )
        val lrc = StructuredLyrics.toTranslationLrc(StructuredLyrics.merge(original, trans))
        assertTrue(lrc.contains("[00:09.000]我这一周过得糟糕透顶"))
        assertTrue(lrc.contains("[00:12.000]整夜自我安慰 佯装也没那么苦"))
        assertTrue(!lrc.contains("[00:00.000]"))
        assertTrue(!lrc.contains("[00:03.000]"))
    }

    @Test
    fun lineOnlyLyricsStillGetEnhancedTimestamps() {
        val lines = listOf(
            TimedLine(9000, 12000, listOf(TimedWord(9000, 12000, "I had a bad week")))
        )
        val raw = StructuredLyrics.toEnhancedLrc(lines)
        assertEquals("[00:09.000]I had a bad week", raw)
        assertTrue(!WordTimedLyricConverter.hasWordTiming(raw))
        assertTrue(!WordTimedLyricConverter.hasRealWordTiming(raw))
    }

    @Test
    fun wordTimedLinesKeepInlineStamps() {
        val lines = listOf(
            TimedLine(
                1000,
                1800,
                listOf(
                    TimedWord(1000, 1300, "Hel"),
                    TimedWord(1300, 1800, "lo")
                )
            )
        )
        val raw = StructuredLyrics.toEnhancedLrc(lines)
        assertTrue(raw.startsWith("[00:01.000]"))
        assertTrue(raw.contains("<00:01.000>Hel"))
        assertTrue(raw.contains("<00:01.300>lo"))
        assertTrue(raw.contains("<00:01.800>"))
        assertTrue(WordTimedLyricConverter.hasRealWordTiming(raw))
    }

    @Test
    fun doesNotBindTranslationToTitleHeader() {
        val original = listOf(
            TimedLine(0, 4340, listOf(TimedWord(0, 4340, "One And Only - Adele"))),
            TimedLine(4340, 8686, listOf(TimedWord(4340, 8686, "Written by: Adele Adkins/Dan Wilson/Greg Wells"))),
            TimedLine(8686, 12876, listOf(TimedWord(8686, 12876, "You've been on my mind"))),
            TimedLine(12876, 17734, listOf(TimedWord(12876, 17734, "I grow fonder every day"))),
            TimedLine(17734, 22634, listOf(TimedWord(17734, 22634, "Lose myself in time")))
        )
        val trans = listOf(
            TimedLine(0, 8686, listOf(TimedWord(0, 8686, "你一直在我心中"))),
            TimedLine(8686, 12876, listOf(TimedWord(8686, 12876, "每天我都会更加渴望你"))),
            TimedLine(12876, 17734, listOf(TimedWord(12876, 17734, "在时间中迷失了自己")))
        )
        val lrc = StructuredLyrics.toTranslationLrc(StructuredLyrics.merge(original, trans))
        assertTrue(lrc.contains("[00:08.686]你一直在我心中"))
        assertTrue(lrc.contains("[00:12.876]每天我都会更加渴望你"))
        assertTrue(lrc.contains("[00:17.734]在时间中迷失了自己"))
        assertTrue(!lrc.contains("[00:00.000]"))
        assertTrue(!lrc.contains("[00:04.340]"))
    }
}
