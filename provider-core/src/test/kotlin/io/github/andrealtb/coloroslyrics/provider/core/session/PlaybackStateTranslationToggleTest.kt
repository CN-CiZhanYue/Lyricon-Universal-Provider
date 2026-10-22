/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.session

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateTranslationToggleTest {
    @Test
    fun publicActionIdMatchesBridgeContract() {
        assertEquals(
            "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION",
            PlaybackStateTranslationToggle.ACTION_ID
        )
        assertEquals("翻译", PlaybackStateTranslationToggle.ACTION_NAME)
        assertEquals("cll.translation.poke", PlaybackStateTranslationToggle.POKE_EXTRA)
    }

    @Test
    fun alreadyHasPublicActionDetectsExactActionId() {
        assertFalse(
            PlaybackStateTranslationToggle.alreadyHasPublicAction(
                listOf("com.salt.music.desktop_lyrics", "coneplayer.notification.favorite")
            )
        )
        assertTrue(
            PlaybackStateTranslationToggle.alreadyHasPublicAction(
                listOf(
                    PlaybackStateTranslationToggle.ACTION_ID,
                    "com.salt.music.desktop_lyrics"
                )
            )
        )
    }

    @Test
    fun placeholderIconPrefersExistingCustomActionThenHostIcon() {
        assertEquals(
            42,
            PlaybackStateTranslationToggle.resolvePlaceholderIcon(listOf(0, 42), 7)
        )
        assertEquals(
            7,
            PlaybackStateTranslationToggle.resolvePlaceholderIcon(listOf(0, 0), 7)
        )
        assertEquals(
            PlaybackStateTranslationToggle.FALLBACK_ICON,
            PlaybackStateTranslationToggle.resolvePlaceholderIcon(emptyList(), 0)
        )
    }
}
