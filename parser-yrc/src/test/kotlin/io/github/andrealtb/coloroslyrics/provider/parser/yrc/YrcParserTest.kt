/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.parser.yrc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YrcParserTest {

    @Test
    fun wordBeginsAreClampedToTheParsedLineBegin() {
        val line = YrcParser.parse("[1000,500](0,100,0)foo(1050,50,0)bar").single()
        val words = line.words.orEmpty()

        assertEquals(1_000L, line.begin)
        assertEquals(listOf(1_000L, 1_050L), words.map { it.begin })
        assertTrue(words.all { it.begin >= line.begin })
        assertTrue(words.all { it.end >= it.begin })
    }

    @Test
    fun keepsZeroDurationCommaAndFullwidthAsideAsSeparateSyllables() {
        val line = YrcParser.parse(
            "[11430,2610](11430,450,0)Bad(11880,0,0), (11880,360,0)bad " +
                "(12240,300,0)boy(12540,0,0), (12540,360,0)shiny"
        ).single()
        val words = line.words.orEmpty()
        assertEquals(listOf("Bad", ", ", "bad ", "boy", ", ", "shiny"), words.map { it.text })
        assertEquals(listOf(11_430L, 11_880L, 11_880L, 12_240L, 12_540L, 12_540L), words.map { it.begin })
    }

    @Test
    fun negativePreRollLineAndWordsStillClampToZero() {
        val line = YrcParser.parse("[-100,200](-50,30,0)foo").single()
        val word = line.words.orEmpty().single()

        assertEquals(0L, line.begin)
        assertEquals(0L, word.begin)
        assertEquals(30L, word.end)
    }
}
