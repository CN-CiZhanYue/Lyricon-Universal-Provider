/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.qrc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrcParserTest {

    @Test
    fun testParseSimpleQrc() {
        val content = """
            [ti:Test Title]
            [ar:Test Artist]
            [1000,2000]Hello(1000,1000) World(2000,1000)
            [3000,2000]Second(3000,1000) Line(4000,1000)
        """.trimIndent()

        val (meta, lines) = QrcParser.parseLyric(content)
        assertEquals("Test Title", meta["ti"])
        assertEquals("Test Artist", meta["ar"])
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].begin)
        assertEquals(3000L, lines[0].end)
        assertEquals("Hello World", lines[0].text)
    }

    @Test
    fun testParseXmlQrc() {
        val xml = """<Lyric LyricContent="[ti:Song][1000,1000]Line 1(1000,1000)"/>"""
        val result = QrcParser.parseXML(xml)
        assertEquals(1, result.size)
        assertEquals("Song", result[0].metaData["ti"])
        assertEquals(1, result[0].lines.size)
        assertEquals("Line 1", result[0].lines[0].text)
    }
}
