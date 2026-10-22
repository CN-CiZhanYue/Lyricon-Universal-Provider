package io.github.cn_cizhanyue.lyricon.universal.provider.api

import okhttp3.OkHttpClient
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BetterLyricsSourceTest {
    private val source = BetterLyricsSource(OkHttpClient())

    @Test
    fun acceptsAppleTtmlSecondsBeforeFirstMinute() {
        assertEquals("00:05.347", source.toLrcStamp("5.347"))
        assertEquals("01:09.700", source.toLrcStamp("1:09.700"))
    }

    @Test
    fun preservesInterSpanSpacesAndEarlyLines() {
        val lrc = source.ttmlToLrc(
            """<tt><body><div><p begin="5.347" end="10.235"><span begin="5.347" end="5.711">I</span> <span begin="5.711" end="5.929">have</span></p></div></body></tt>"""
        )
        assertTrue(lrc.startsWith("[00:05.347]"))
        assertTrue(lrc.contains(">I <"))
        assertTrue(lrc.contains(">have"))
    }
}
