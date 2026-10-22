/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.lrc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnhanceLrcParserTest {

    @Test
    @DisplayName("解析标准LRC格式 - 验证基础时间轴和文本")
    fun testParseStandardLrc() {
        val lrc = """
            [ti:测试歌曲]
            [00:10.20]第一行歌词
            [00:12.50]第二行歌词
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals("测试歌曲", doc.metadata["ti"])
        assertEquals(2, doc.lines.size)
        assertEquals(10200L, doc.lines[0].begin)
        assertEquals("第一行歌词", doc.lines[0].text)
        assertEquals(12500L, doc.lines[1].begin)
    }

    @Test
    @DisplayName("解析复杂正文 - 验证包含[]和/的内容")
    fun testParseTextWithBrackets() {
        val lrc = """
            [00:32.99]【/Prologue】
            [00:33.11]何時[人類]向天空築起了[巴別塔]
            [01:15.54][天才]咬下了「苹果」
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(3, doc.lines.size)
        assertEquals("【/Prologue】", doc.lines[0].text)
        assertEquals("何時[人類]向天空築起了[巴別塔]", doc.lines[1].text)
        assertEquals("[天才]咬下了「苹果」", doc.lines[2].text)
        assertEquals(75540L, doc.lines[2].begin)
    }

    @Test
    @DisplayName("解析多时间戳行 - 验证同一行歌词对应多个时间点")
    fun testParseMultipleTags() {
        val lrc = "[00:10.00][00:20.00]重复的歌词"
        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(2, doc.lines.size)
        assertEquals(10000L, doc.lines[0].begin)
        assertEquals(20000L, doc.lines[1].begin)
        assertEquals("重复的歌词", doc.lines[0].text)
        assertEquals("重复的歌词", doc.lines[1].text)
    }

    @Test
    @DisplayName("解析逐字时间轴 - 验证LyricWord解析")
    fun testParseKaraokeWords() {
        val lrc = "[00:10.00]<00:10.00>Hello <00:10.50>World"
        val doc = EnhanceLrcParser.parse(lrc)

        val line = doc.lines[0]
        assertNotNull(line.words)
        assertEquals(2, line.words?.size)
        assertEquals("Hello ", line.words?.get(0)?.text)
        assertEquals(500L, line.words?.get(0)?.duration)
        assertEquals("World", line.words?.get(1)?.text)
    }

    @Test
    fun repeatedLineTagsShiftWordTimelineWithoutCreatingTranslation() {
        val doc = EnhanceLrcParser.parse(
            "[00:10.000][00:20.000]<00:10.000>Hi <00:10.500>there<00:11.000>"
        )

        assertEquals(2, doc.lines.size)
        assertEquals(listOf(10_000L, 20_000L), doc.lines.map { it.begin })
        assertEquals(listOf(10_000L, 10_500L), doc.lines[0].words.orEmpty().map { it.begin })
        assertEquals(listOf(20_000L, 20_500L), doc.lines[1].words.orEmpty().map { it.begin })
        assertTrue(doc.lines.all { it.secondary.isNullOrBlank() })
    }

    @Test
    fun offsetMovesSecondaryWordTimelineAndUtf8BomDoesNotDropFirstLine() {
        val doc = EnhanceLrcParser.parse(
                "\uFEFF[offset:1000]\n" +
                "[00:10.000]<00:10.000>Main<00:11.000>\n" +
                "[bg:<00:10.000>Back]"
        )

        assertEquals(11_000L, doc.lines.single().begin)
        assertEquals(11_000L, doc.lines.single().secondaryWords.orEmpty().single().begin)
    }

    @Test
    @DisplayName("解析方括号行内逐字时间轴并移除正文时间标签")
    fun testParseBracketInlineKaraokeWords() {
        val lrc = """
            [00:11.367]I [00:11.548]heard [00:11.773]you [00:12.062]calling[00:13.370]
            [00:11.367]我听见你的呼唤声
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(1, doc.lines.size)
        val line = doc.lines.single()
        assertEquals("I heard you calling", line.text)
        assertEquals("我听见你的呼唤声", line.secondary)
        assertFalse(line.text.orEmpty().contains("[00:"))
        assertEquals(4, line.words?.size)
        assertEquals(11_367L, line.words?.get(0)?.begin)
        assertEquals(11_548L, line.words?.get(0)?.end)
        assertEquals("I ", line.words?.get(0)?.text)
        assertEquals(13_370L, line.words?.last()?.end)
        assertEquals("calling", line.words?.last()?.text)
    }

    @Test
    @DisplayName("方括号结尾时间只关闭前一词而不创建空词")
    fun testBracketInlineTerminalTimestampClosesLastWord() {
        val doc = EnhanceLrcParser.parse("[00:01.000]Hello [00:01.400]world[00:02.000]")

        val words = doc.lines.single().words.orEmpty()
        assertEquals(2, words.size)
        assertEquals("Hello ", words[0].text)
        assertEquals("world", words[1].text)
        assertEquals(2_000L, words[1].end)
        assertEquals(600L, words[1].duration)
    }

    @Test
    @DisplayName("多行起始时间标签不误判为方括号逐字格式")
    fun testMultipleLineTagsRemainRepeatedLines() {
        val doc = EnhanceLrcParser.parse("[00:10.00][00:20.00]重复的歌词")

        assertEquals(2, doc.lines.size)
        assertEquals(listOf(10_000L, 20_000L), doc.lines.map { it.begin })
        assertTrue(doc.lines.all { it.text == "重复的歌词" && it.words.isNullOrEmpty() })
    }

    @Test
    @DisplayName("解析角色标签 - 验证主唱与背景音逻辑")
    fun testParseRolesAndAlignment() {
        val lrc = """
            [00:10.00]v1: 主唱内容
            [00:12.00]bg: 背景伴唱
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals("主唱内容", doc.lines[0].text)
        assertFalse(doc.lines[0].isAlignedRight)
        assertEquals("背景伴唱", doc.lines[1].text)
        assertTrue(doc.lines[1].isAlignedRight)
    }

    @Test
    @DisplayName("解析独立bg元数据 - 验证与上一行合并")
    fun testHandleBackgroundMeta() {
        val lrc = """
            [00:10.00]Hello World
            [bg: (Background Voice)]
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(1, doc.lines.size)
        val line = doc.lines[0]
        assertEquals("Hello World", line.text)
        assertEquals("(Background Voice)", line.secondary)
    }

    @Test
    @DisplayName("验证时间补全 - 确保最后一行有duration")
    fun testFinalizeDuration() {
        val totalDuration = 60000L
        val lrc = "[00:50.00]最后一行"

        val doc = EnhanceLrcParser.parse(lrc, duration = totalDuration)

        val lastLine = doc.lines.last()
        assertEquals(50000L, lastLine.begin)
        assertEquals(60000L, lastLine.end)
        assertEquals(10000L, lastLine.duration)
    }

    @Test
    @DisplayName("验证Offset偏移量 - 验证整体时间移动")
    fun testApplyOffset() {
        val lrc = """
            [offset:1000]
            [00:10.00]歌词
        """.trimIndent()

        val doc = EnhanceLrcParser.parse(lrc)

        assertEquals(11000L, doc.lines[0].begin)
    }
}
