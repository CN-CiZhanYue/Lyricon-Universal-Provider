package io.github.cn_cizhanyue.lyricon.universal.provider

import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalLyricPayloadPolicyTest {
    private val wordRaw = "[00:01.000]<00:01.000>Hello <00:01.500>world<00:02.000>\n" +
        "[00:03.000]<00:03.000>Good <00:03.400>night<00:04.000>"
    private val lineLyric = "[00:01.000]Hello world\n[00:03.000]Good night"
    private val translation = "[00:01.000]你好世界\n[00:03.000]晚安"

    private fun cached(raw: String? = wordRaw) = JSONObject().apply {
        put("lyric", lineLyric)
        if (raw != null) put("rawLyric", raw)
        put("transLyric", translation)
        put("translationLyric", translation)
        put("translationLanguageTag", "zh-CN")
    }

    @Test
    fun `word timing off keeps a line-timed rawLyric so Bridge still renders the translation`() {
        val json = cached()
        val result = UniversalLyricPayloadPolicy.apply(json, wordTiming = false, rawLyric = true, translation = true)

        assertEquals(lineLyric, json.getString("rawLyric"))
        assertEquals(translation, json.getString("translationLyric"))
        assertTrue(result.rawAttached)
        assertFalse(result.wordTimed)
        // Idempotent: in-memory payloads may pass through the policy again on a switch change.
        val again = JSONObject(json.toString())
        UniversalLyricPayloadPolicy.apply(again, wordTiming = false, rawLyric = true, translation = true)
        assertEquals(json.toString(), again.toString())
    }

    @Test
    fun `word timing on publishes the word-timed rawLyric verbatim`() {
        val json = cached()
        val result = UniversalLyricPayloadPolicy.apply(json, wordTiming = true, rawLyric = true, translation = true)

        assertEquals(wordRaw, json.getString("rawLyric"))
        assertTrue(result.wordTimed)
    }

    @Test
    fun `payload cached without rawLyric feeds the line model from its timed lyric`() {
        val json = cached(raw = null)
        UniversalLyricPayloadPolicy.apply(json, wordTiming = true, rawLyric = true, translation = true)

        assertEquals(lineLyric, json.getString("rawLyric"))
    }

    @Test
    fun `rawLyric and translation switches strip only their own channels`() {
        val json = cached()
        val result = UniversalLyricPayloadPolicy.apply(json, wordTiming = true, rawLyric = false, translation = false)

        assertFalse(json.has("rawLyric"))
        assertFalse(json.has("transLyric") || json.has("translationLyric") || json.has("translationLanguageTag"))
        assertEquals(lineLyric, json.getString("lyric"))
        assertFalse(result.rawAttached)
    }

    @Test
    fun `no-lyric payload never gains a rawLyric`() {
        val json = JSONObject(NoLyricPolicy.payloadJson("Song", "Artist", NoLyricPolicy.REASON_MISS))
        UniversalLyricPayloadPolicy.apply(json, wordTiming = false, rawLyric = true, translation = true)

        assertFalse(json.has("rawLyric"))
        assertTrue(json.getBoolean("noLyric"))
    }
}
