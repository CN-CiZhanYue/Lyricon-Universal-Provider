/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.session

import android.content.Context
import android.media.session.PlaybackState
import android.os.Bundle

/**
 * Injects the public ColorOS lyric-translation CustomAction into a player
 * [PlaybackState]. SystemUI binds clicks; the player must ignore the callback.
 *
 * ColorOS [PlaybackState.Builder] copy-from-existing can drop position / update
 * time the same way [android.media.MediaMetadata.Builder] collapses bitmaps.
 * Always seed an empty builder and copy host fields explicitly. Always return a
 * new instance so ColorOS rebinds Rule0 instead of keeping the previous card.
 */
object PlaybackStateTranslationToggle {
    const val ACTION_ID = "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION"
    const val ACTION_NAME = "翻译"
    const val FALLBACK_ICON = android.R.drawable.ic_menu_manage
    const val POKE_EXTRA = "cll.translation.poke"

    fun alreadyHasPublicAction(actionIds: Iterable<String?>): Boolean =
        actionIds.any { it == ACTION_ID }

    fun resolvePlaceholderIcon(existingIcons: Iterable<Int>, hostAppIcon: Int): Int {
        existingIcons.firstOrNull { it != 0 }?.let { return it }
        if (hostAppIcon != 0) return hostAppIcon
        return FALLBACK_ICON
    }

    @JvmOverloads
    fun prependPublicAction(
        original: PlaybackState,
        hostContext: Context?,
        pokeToken: Long? = null
    ): PlaybackState {
        return runCatching {
            val originalActions = original.customActions.orEmpty()
            val builder = copyHostState(original)
            val existingTranslation = originalActions.firstOrNull { it.action == ACTION_ID }
            if (existingTranslation != null) {
                builder.addCustomAction(existingTranslation)
            } else {
                builder.addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_ID,
                        ACTION_NAME,
                        resolvePlaceholderIcon(
                            originalActions.map { it.icon },
                            hostContext?.applicationInfo?.icon ?: 0
                        )
                    ).build()
                )
            }
            originalActions.forEach { existing ->
                if (existing.action != ACTION_ID) {
                    builder.addCustomAction(existing)
                }
            }
            if (pokeToken != null) {
                val extras = original.extras?.let { Bundle(it) } ?: Bundle()
                extras.putLong(POKE_EXTRA, pokeToken)
                builder.setExtras(extras)
            }
            builder.build()
        }.getOrDefault(original)
    }

    fun removePublicAction(original: PlaybackState): PlaybackState {
        val originalActions = original.customActions.orEmpty()
        if (originalActions.none { it.action == ACTION_ID }) return original
        return runCatching {
            copyHostState(original).apply {
                originalActions.forEach { existing ->
                    if (existing.action != ACTION_ID) addCustomAction(existing)
                }
            }.build()
        }.getOrDefault(original)
    }

    internal fun copyHostState(original: PlaybackState): PlaybackState.Builder {
        val builder = PlaybackState.Builder()
        builder.setState(
            original.state,
            original.position,
            original.playbackSpeed,
            original.lastPositionUpdateTime
        )
        builder.setBufferedPosition(original.bufferedPosition)
        builder.setActions(original.actions)
        builder.setActiveQueueItemId(original.activeQueueItemId)
        original.errorMessage?.let { builder.setErrorMessage(it) }
        original.extras?.let { builder.setExtras(Bundle(it)) }
        return builder
    }
}
