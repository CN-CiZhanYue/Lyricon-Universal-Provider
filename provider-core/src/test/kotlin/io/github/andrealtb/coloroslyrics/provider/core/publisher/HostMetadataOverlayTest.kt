/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.publisher

import io.github.andrealtb.coloroslyrics.provider.core.publisher.HostMetadataOverlay.Result
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HostMetadataOverlayTest {
    private val key = HostMetadataOverlay.KEY_LYRIC_INFO

    @Test
    fun writesOnlyTheRequestedKeyAndKeepsEveryOtherValue() {
        val artwork = Any()
        val store = FakeStore()
        val metadata = FakeMetadata(mutableMapOf("title" to "Song", "art" to artwork))

        val outcome = MetadataOverlayEngine(store).putGuarded(metadata, key, "{\"lyric\":1}")

        assertEquals(Result.WRITTEN, outcome.result)
        assertEquals(100, outcome.parcelBytes)
        assertEquals("{\"lyric\":1}", metadata.values[key])
        assertEquals("Song", metadata.values["title"])
        assertSame(artwork, metadata.values["art"])
        assertEquals(3, metadata.values.size)
    }

    @Test
    fun identicalValueIsNotWrittenAgain() {
        val store = FakeStore()
        val metadata = FakeMetadata(mutableMapOf(key to "same"))

        val outcome = MetadataOverlayEngine(store).putGuarded(metadata, key, "same")

        assertEquals(Result.UNCHANGED, outcome.result)
        assertEquals(0, store.writes)
    }

    @Test
    fun oversizedParcelRestoresTheHostValue() {
        val store = FakeStore(parcelBytes = NativeLyricInfoPublisher.MAX_PARCEL_BYTES + 1)
        val metadata = FakeMetadata(mutableMapOf(key to "host"))

        val outcome = MetadataOverlayEngine(store).putGuarded(metadata, key, "module")

        assertEquals(Result.PARCEL_TOO_LARGE, outcome.result)
        assertEquals("host", metadata.values[key])
    }

    @Test
    fun rejectedWriteRemovesAKeyTheHostNeverHad() {
        val store = FakeStore(parcelBytes = null)
        val metadata = FakeMetadata(mutableMapOf("title" to "Song"))

        val outcome = MetadataOverlayEngine(store).putGuarded(metadata, key, "module")

        assertEquals(Result.MEASUREMENT_FAILED, outcome.result)
        assertFalse(metadata.values.containsKey(key))
    }

    @Test
    fun oversizedFieldIsRejectedBeforeTouchingTheMetadata() {
        val store = FakeStore()
        val metadata = FakeMetadata(mutableMapOf())
        val huge = "x".repeat(NativeLyricInfoPublisher.MAX_LYRIC_FIELD_CHARS + 1)

        val outcome = MetadataOverlayEngine(store).putGuarded(metadata, key, huge)

        assertEquals(Result.FIELD_TOO_LARGE, outcome.result)
        assertEquals(0, store.writes)
    }

    @Test
    fun unreachableBundleFailsOpen() {
        val store = FakeStore(writable = false)
        val metadata = FakeMetadata(mutableMapOf(key to "host"))
        val engine = MetadataOverlayEngine(store)

        assertEquals(Result.UNSUPPORTED, engine.putGuarded(metadata, key, "module").result)
        assertEquals(Result.UNSUPPORTED, engine.clear(metadata, key))
        assertEquals(Result.UNSUPPORTED, engine.putText(metadata, "title", "Song"))
        assertEquals("host", metadata.values[key])
        assertEquals(0, store.writes)
    }

    @Test
    fun hostValueSurvivesModuleWritesIntoTheSameObject() {
        val store = FakeStore()
        val engine = MetadataOverlayEngine(store)
        val patched = FakeMetadata(mutableMapOf(key to "official"))
        val untouched = FakeMetadata(mutableMapOf(key to "other"))
        val bare = FakeMetadata(mutableMapOf())

        engine.putGuarded(patched, key, "module-1")
        engine.putGuarded(patched, key, "module-2")
        engine.putGuarded(bare, key, "module")

        assertEquals("module-2", patched.values[key])
        assertEquals("official", engine.hostValue(patched, key))
        assertEquals("other", engine.hostValue(untouched, key))
        assertNull(engine.hostValue(bare, key))
    }

    @Test
    fun clearOverwritesOnlyANonEmptyValue() {
        val store = FakeStore()
        val engine = MetadataOverlayEngine(store)
        val stale = FakeMetadata(mutableMapOf(key to "stale"))
        val empty = FakeMetadata(mutableMapOf(key to ""))
        val absent = FakeMetadata(mutableMapOf())

        assertEquals(Result.WRITTEN, engine.clear(stale, key))
        assertEquals(Result.UNCHANGED, engine.clear(empty, key))
        assertEquals(Result.UNCHANGED, engine.clear(absent, key))
        assertEquals("", stale.values[key])
        assertFalse(absent.values.containsKey(key))
        assertEquals("stale", engine.hostValue(stale, key))
    }

    @Test
    fun putTextReplacesOnlyThatKey() {
        val artwork = Any()
        val store = FakeStore()
        val engine = MetadataOverlayEngine(store)
        val metadata = FakeMetadata(mutableMapOf("title" to "lyric line", "art" to artwork))

        assertEquals(Result.WRITTEN, engine.putText(metadata, "title", "Song"))
        assertEquals(Result.UNCHANGED, engine.putText(metadata, "title", "Song"))
        assertEquals("Song", metadata.values["title"])
        assertSame(artwork, metadata.values["art"])
        assertEquals(1, store.writes)
    }

    @Test
    fun originalsAreBoundedAndReleasedObjectsDoNotPinOthers() {
        val store = FakeStore()
        val engine = MetadataOverlayEngine(store, trackedObjects = 2)
        val first = FakeMetadata(mutableMapOf(key to "first"))
        val second = FakeMetadata(mutableMapOf(key to "second"))
        val third = FakeMetadata(mutableMapOf(key to "third"))

        engine.putGuarded(first, key, "m1")
        engine.putGuarded(second, key, "m2")
        engine.putGuarded(third, key, "m3")

        // The oldest original was evicted, so only its current value remains readable.
        assertEquals("m1", engine.hostValue(first, key))
        assertEquals("second", engine.hostValue(second, key))
        assertEquals("third", engine.hostValue(third, key))
        assertTrue(store.writes == 3)
    }

    private class FakeMetadata(val values: MutableMap<String, Any>)

    private class FakeStore(
        private val parcelBytes: Int? = 100,
        private val writable: Boolean = true
    ) : MetadataOverlayEngine.Store<FakeMetadata> {
        var writes = 0

        override fun isWritable(metadata: FakeMetadata): Boolean = writable

        override fun contains(metadata: FakeMetadata, key: String): Boolean =
            metadata.values.containsKey(key)

        override fun read(metadata: FakeMetadata, key: String): String? =
            metadata.values[key] as? String

        override fun write(metadata: FakeMetadata, key: String, value: String) {
            writes++
            metadata.values[key] = value
        }

        override fun remove(metadata: FakeMetadata, key: String) {
            metadata.values.remove(key)
        }

        override fun measureParcelBytes(metadata: FakeMetadata): Int? = parcelBytes
    }
}
