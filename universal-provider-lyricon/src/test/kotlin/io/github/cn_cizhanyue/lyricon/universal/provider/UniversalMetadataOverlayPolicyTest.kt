package io.github.cn_cizhanyue.lyricon.universal.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalMetadataOverlayPolicyTest {
    @Test
    fun `forced generation-guarded replay replaces an existing host payload`() {
        assertFalse(
            UniversalMetadataOverlayPolicy.shouldPreserveExisting(
                "{\"lyric\":\"[00:01.00]line\"}",
                force = true
            )
        )
    }

    @Test
    fun `passive playback refresh preserves an existing host payload`() {
        assertTrue(
            UniversalMetadataOverlayPolicy.shouldPreserveExisting(
                "{\"lyric\":\"[00:01.00]line\"}",
                force = false
            )
        )
    }

    @Test
    fun `passive refresh may update an existing universal payload`() {
        assertFalse(
            UniversalMetadataOverlayPolicy.shouldPreserveExisting(
                "{\"remark\":\"[CLLUniversal]\"}",
                force = false
            )
        )
    }
}
