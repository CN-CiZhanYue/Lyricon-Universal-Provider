package io.github.cn_cizhanyue.lyricon.universal.provider

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UniversalLyricHistoryTest {
    private fun snapshot(title: String, status: String, source: String = "none", history: String = "") =
        UniversalSnapshotUi.parse(
            "package=player\ntitle=$title\nartist=Artist\ngeneration=3\n" +
                "lyricSource=$source\nlyricStatus=$status\ncachedSongs=1\n" +
                UniversalLyricHistory.snapshotLines(history)
        )

    private fun row(title: String, status: String, source: String = "universal/qq") =
        UniversalLyricHistory.row(title, "Artist", source, status)!!

    @Test
    fun `finished fetch replaces the pending row of the same track`() {
        val pending = UniversalLyricHistory.update("", snapshot("Song", "pending"))!!
        val ready = UniversalLyricHistory.update(pending, snapshot("Song", "success", "universal/qq"))!!

        val rows = UniversalLyricHistory.parse(ready)
        assertEquals(1, rows.size)
        assertEquals(UniversalLyricHistory.Entry("Song", "Artist", "qq", "success"), rows.single())
    }

    @Test
    fun `rows stay newest first and bounded while missing tracks are ignored`() {
        var rows = ""
        (1..7).forEach { rows = UniversalLyricHistory.update(rows, snapshot("Song $it", "success"))!! }

        assertNull(UniversalLyricHistory.update(rows, snapshot("(missing)", "pending")))
        assertEquals(
            listOf("Song 7", "Song 6", "Song 5", "Song 4", "Song 3"),
            UniversalLyricHistory.parse(rows).map { it.title }
        )
    }

    @Test
    fun `tracks played while the app was stopped arrive with the next snapshot`() {
        // system_server saw A, B and C; the app last stored an older track and A still pending.
        var server = ""
        server = UniversalLyricHistory.moveToTop(server, row("A", "pending"))
        server = UniversalLyricHistory.moveToTop(server, row("B", "pending"))
        server = UniversalLyricHistory.replaceInPlace(server, row("A", "success"))
        server = UniversalLyricHistory.moveToTop(server, row("C", "success"))
        server = UniversalLyricHistory.replaceInPlace(server, row("B", "noLyric", "none"))
        val stored = listOf(row("A", "pending"), row("Old", "success")).joinToString("\n")

        val merged = UniversalLyricHistory.update(stored, snapshot("C", "success", "universal/qq", server))!!

        assertEquals(
            listOf("C" to "success", "B" to "noLyric", "A" to "success", "Old" to "success"),
            UniversalLyricHistory.parse(merged).map { it.title to it.status }
        )
    }

    @Test
    fun `stored rows stuck fetching are dropped once system_server reports its rows`() {
        val stored = listOf(
            row("Stuck", "pending"),
            "Legacy\tQQ音乐\t正在获取",
            row("Done", "success"),
            row("Current", "pending")
        ).joinToString("\n")
        val server = row("Current", "pending")

        val merged = UniversalLyricHistory.update(stored, snapshot("Current", "pending", history = server))!!
        assertEquals(listOf("Current", "Done"), UniversalLyricHistory.parse(merged).map { it.title })

        // Without system_server rows (an older module build) nothing is dropped.
        val legacy = UniversalLyricHistory.update(stored, snapshot("Current", "pending"))!!
        assertEquals(4, UniversalLyricHistory.parse(legacy).size)
    }

    @Test
    fun `server rows survive the snapshot text and fields cannot break the row format`() {
        val odd = UniversalLyricHistory.row(" Tab\tTitle\n", "Art\rist", "universal/qq", "")!!
        val parsed = snapshot("Tab Title", "pending", history = odd)

        assertEquals(odd, parsed["history0"])
        assertEquals(
            UniversalLyricHistory.Entry("Tab Title", "Art ist", "qq", "fetching"),
            UniversalLyricHistory.parse(odd).single()
        )
    }
}
