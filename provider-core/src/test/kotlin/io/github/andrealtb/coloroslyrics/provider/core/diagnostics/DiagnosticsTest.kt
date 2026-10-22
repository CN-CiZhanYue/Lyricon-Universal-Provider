/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.diagnostics

import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeMode
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsTest {

    @After
    fun tearDown() {
        StructuredDiagnostics.resetForTesting()
    }

    @Test
    fun testThrottlerThrottlesRepeatedKeysWithinWindow() {
        val throttler = DiagnosticThrottler(windowMillis = 1000L)
        val t0 = 10000L
        assertTrue(throttler.shouldLog("event-1", t0))
        assertFalse(throttler.shouldLog("event-1", t0 + 200))
        assertFalse(throttler.shouldLog("event-1", t0 + 999))
        assertTrue(throttler.shouldLog("event-1", t0 + 1000))
    }

    @Test
    fun throttlerEvictsOldKeysAtConfiguredBound() {
        val throttler = DiagnosticThrottler(windowMillis = 1000L, maxEntries = 2)
        assertTrue(throttler.shouldLog("one", 100L))
        assertTrue(throttler.shouldLog("two", 100L))
        assertTrue(throttler.shouldLog("three", 100L))
        assertEquals(2, throttler.entryCount())
        assertTrue(throttler.shouldLog("one", 200L))
    }

    @Test
    fun testSensitiveFieldRedactorRedactsTokensAndPasswords() {
        val msg = "User token=abc123def456ghi789 and cookie=xyz_secret_val with password=my_secret_pass"
        val redacted = SensitiveFieldRedactor.redact(msg)
        assertFalse(redacted.contains("abc123def456ghi789"))
        assertFalse(redacted.contains("xyz_secret_val"))
        assertFalse(redacted.contains("my_secret_pass"))
        assertTrue(redacted.contains("<REDACTED_TOKEN>"))
        assertTrue(redacted.contains("<REDACTED_COOKIE>"))
        assertTrue(redacted.contains("<REDACTED_PWD>"))
    }

    @Test
    fun redactorRemovesSharedStorageAndFileUris() {
        val redacted = SensitiveFieldRedactor.redact(
            "file:///storage/emulated/0/Music/private.lrc /storage/emulated/0/Album/cover.jpg"
        )
        assertFalse(redacted.contains("private.lrc"))
        assertFalse(redacted.contains("cover.jpg"))
    }

    @Test
    fun structuredEventUsesStableFieldOrderAndEscaping() {
        val formatted = DiagnosticEventFormatter.format(
            "INFO",
            DiagnosticEvent(
                component = "provider/salt",
                area = "track",
                event = "TRACK_BOUND",
                mode = RuntimeMode.ROOT_MODULE,
                process = "com.salt.music",
                generation = 42L,
                reason = "new track"
            )
        )

        assertEquals(
            "[CLL] level=INFO component=provider/salt area=track event=TRACK_BOUND " +
                "mode=ROOT_MODULE process=com.salt.music generation=42 reason=\"new track\"",
            formatted
        )
    }

    @Test
    fun allConfiguredSinksReceiveTheSameRedactedEvent() {
        val first = RecordingSink()
        val second = RecordingSink()
        StructuredDiagnostics.configure(debugEnabled = true, additionalSinks = listOf(first, second))

        StructuredDiagnostics.logDebug(
            DiagnosticEvent(
                component = "provider/salt",
                area = "reflection",
                event = "TARGET_FOUND",
                message = "token=abc123def456ghi789"
            )
        )

        assertEquals(1, first.messages.size)
        assertEquals(first.messages, second.messages)
        assertFalse(first.messages.single().contains("abc123def456ghi789"))
        assertTrue(first.messages.single().contains("<REDACTED_TOKEN>"))
    }

    @Test
    fun diagnosticTrackHashIsStableAndDoesNotExposeIdentity() {
        val first = DiagnosticHasher.sha256("track-id|private title|private artist|180")
        val second = DiagnosticHasher.sha256("track-id|private title|private artist|180")

        assertEquals(first, second)
        assertTrue(first.startsWith("sha256:"))
        assertFalse(first.contains("private"))
    }

    private class RecordingSink : DiagnosticSink {
        val messages = mutableListOf<String>()

        override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
            messages += message
        }
    }
}
