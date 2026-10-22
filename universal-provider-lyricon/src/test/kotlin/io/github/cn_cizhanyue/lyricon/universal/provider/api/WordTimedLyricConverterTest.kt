package io.github.cn_cizhanyue.lyricon.universal.provider.api

import org.junit.Test
import kotlin.test.assertTrue

class WordTimedLyricConverterTest {
    @Test
    fun convertsQrcXmlToEnhancedLrc() {
        val xml = """<Lyric LyricContent="[1000,800]Hel(1000,300)lo(1300,200)"/>"""
        val converted = WordTimedLyricConverter.convert(xml)
        assertTrue(converted != null && converted.contains("[00:01.000]"))
        assertTrue(WordTimedLyricConverter.hasWordTiming(converted))
        assertTrue(converted!!.contains("<00:01.000>Hel"))
    }

    @Test
    fun syntheticSingleWordWrapperIsNotRealWordTiming() {
        val synthetic = "[00:00.482]<00:00.482>ファタール<00:03.539>"
        assertTrue(WordTimedLyricConverter.hasWordTiming(synthetic))
        assertTrue(!WordTimedLyricConverter.hasRealWordTiming(synthetic))
        val real = "[00:01.000]<00:01.000>Hel<00:01.300>lo<00:01.800>"
        assertTrue(WordTimedLyricConverter.hasRealWordTiming(real))
    }
}

