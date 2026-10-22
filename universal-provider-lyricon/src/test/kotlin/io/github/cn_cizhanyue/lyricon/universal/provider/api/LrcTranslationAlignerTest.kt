package io.github.cn_cizhanyue.lyricon.universal.provider.api

import org.junit.Test
import kotlin.test.assertTrue

class LrcTranslationAlignerTest {
    @Test
    fun remapsTranslationOntoPrimaryTimestamps() {
        val primary = """
            [00:01.000]Hello
            [00:04.000]World
        """.trimIndent()
        val translation = """
            [00:01.120]你好
            [00:04.080]世界
        """.trimIndent()
        val aligned = LrcTranslationAligner.align(primary, translation)
        assertTrue(aligned != null && aligned.contains("[00:01.000]你好"))
        assertTrue(aligned!!.contains("[00:04.000]世界"))
    }
}

