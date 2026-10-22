/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LrcParserTest {

    @Test
    @DisplayName("测试基础解析：标准格式与时间转换")
    fun testBaseParse() {
        val lrc = "[00:01.50]Hello World"
        val document = LrcParser.parse(lrc)

        assertEquals(1, document.lines.size)
        val line = document.lines[0]
        assertEquals(1500L, line.begin)
        assertEquals("Hello World", line.text)
    }

    @Test
    @DisplayName("测试核心优化：修复歌词正文包含中括号时的截断异常")
    fun testBracketsInContent() {
        val lrc = "[00:10.00] [和声] 这是一个测试 [歌词中括号]"
        val document = LrcParser.parse(lrc)

        assertEquals(1, document.lines.size)
        assertEquals("[和声] 这是一个测试 [歌词中括号]", document.lines[0].text)
    }

    @Test
    @DisplayName("测试多时间标签：同一行歌词对应多个时间点")
    fun testMultiTagPerLine() {
        val lrc = "[00:01.00][00:05.00]重复的歌词"
        val document = LrcParser.parse(lrc)

        assertEquals(2, document.lines.size)
        assertEquals(1000L, document.lines[0].begin)
        assertEquals(5000L, document.lines[1].begin)
        assertEquals("重复的歌词", document.lines[0].text)
        assertEquals("重复的歌词", document.lines[1].text)
    }

    @Test
    @DisplayName("测试元数据提取：解析 [ar:xxx] 等标签")
    fun testMetaExtraction() {
        val lrc = """
            [ar: Artist Name]
            [ti: Song Title]
            [offset: 500]
            [00:01.00]Lyrics
        """.trimIndent()

        val document = LrcParser.parse(lrc)

        assertEquals("Artist Name", document.metadata["ar"])
        assertEquals("Song Title", document.metadata["ti"])
        assertEquals(1500L, document.lines[0].begin)
    }

    @Test
    @DisplayName("测试时间戳容错：支持不同的毫秒位数")
    fun testTimeFormatAdaptation() {
        val lrc = """
            [00:01.5] 一位毫秒
            [00:02.50] 两位毫秒
            [00:03.500] 三位毫秒
        """.trimIndent()

        val document = LrcParser.parse(lrc)

        assertEquals(1500L, document.lines[0].begin)
        assertEquals(2500L, document.lines[1].begin)
        assertEquals(3500L, document.lines[2].begin)
    }

    @Test
    @DisplayName("测试边界情况：空字符串与非法行")
    fun testEdgeCases() {
        assertNotNull(LrcParser.parse(null))
        assertNotNull(LrcParser.parse(""))

        val invalidLrc = "这是一行没有标签的纯文本\n[invalid]标签格式错误"
        val document = LrcParser.parse(invalidLrc)
        assertTrue(document.lines.isEmpty())
    }

    @Test
    @DisplayName("测试持续时间计算：修正末行结束时间")
    fun testDurationAndFinalize() {
        val lrc = "[00:01.00]第一句"
        val totalDuration = 10000L
        val document = LrcParser.parse(lrc, totalDuration)

        val line = document.lines[0]
        assertEquals(1000L, line.begin)
        assertEquals(10000L, line.end)
        assertEquals(9000L, line.duration)
    }
}
