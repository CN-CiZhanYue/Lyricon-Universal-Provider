/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugConfig
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugSource
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.libxposed.api.XposedInterface

/**
 * Debug switch read side inside target player processes: the module's own read-only Remote
 * Preferences. Missing capability, read errors or absent groups all resolve to null, which
 * ProviderDebugConfig maps to a disabled:* reason without touching lyric hook installation.
 */
class RemotePreferencesDebugSource(
    private val module: XposedInterface
) : ProviderDebugSource {

    override fun read(provider: ProviderId): Boolean? = runCatching {
        module.getRemotePreferences(ProviderDebugConfig.prefsName(provider))
            .getBoolean(ProviderDebugConfig.KEY_DEBUG_ENABLED, false)
    }.getOrNull()
}
