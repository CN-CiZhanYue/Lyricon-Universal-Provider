package io.github.cn_cizhanyue.lyricon.universal.provider

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UniversalSnapshotUiTest {
    @Test
    fun changingBluetoothLineDoesNotChangeUiState() {
        val track = "package=player\ntitle=Song\nartist=Artist\ngeneration=7\n"
        assertEquals(
            UniversalSnapshotUi.parse(track + "rawTitle=line one\nselectionRevision=1"),
            UniversalSnapshotUi.parse(track + "rawTitle=line two\nselectionRevision=2")
        )
    }

    @Test
    fun trackStatusAndCacheChangesRemainObservable() {
        val track = "package=player\ntitle=A=B\nartist=Artist\ngeneration=7\n"
        val pending = UniversalSnapshotUi.parse(track + "lyricStatus=pending\ncachedSongs=1")
        val ready = UniversalSnapshotUi.parse(track + "lyricStatus=success\ncachedSongs=2")
        assertEquals("A=B", ready["title"])
        assertNotEquals(pending, ready)
        assertNotEquals(ready, UniversalSnapshotUi.parse(track.replace("generation=7", "generation=8")))
    }
}
