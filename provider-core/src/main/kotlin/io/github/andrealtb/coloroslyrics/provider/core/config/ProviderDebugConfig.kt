/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.config

import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticSink
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeMode

enum class ProviderId(val configKey: String) {
    NETEASE("netease"),
    QQ("qq"),
    KUGOU("kugou"),
    KUWO("kuwo"),
    SPOTIFY("spotify"),
    LX("lx"),
    POWERAMP("poweramp"),
    SALT("salt"),
    CONE("cone"),
    QISHUI("qishui"),
    MUSICFREE("musicfree"),
    GRAMOPHONE("gramophone"),
    SYMFONIUM("symfonium"),
    METROLIST("metrolist"),
    APPLE("apple")
}

fun interface ProviderDebugSource {
    fun read(provider: ProviderId): Boolean?
}

data class ProviderDebugResolution(
    val enabled: Boolean,
    val reason: String
)

object ProviderDebugConfig {

    const val PREFS_NAME_SUFFIX = "_provider_debug_prefs"
    const val KEY_DEBUG_ENABLED = "provider_debug_logging_enabled"

    fun prefsName(provider: ProviderId): String = "${provider.configKey}$PREFS_NAME_SUFFIX"

    fun resolve(
        mode: RuntimeMode,
        provider: ProviderId,
        rootSource: ProviderDebugSource? = null
    ): Boolean = resolveDetailed(mode, provider, rootSource).enabled

    fun resolveDetailed(
        mode: RuntimeMode,
        provider: ProviderId,
        rootSource: ProviderDebugSource? = null
    ): ProviderDebugResolution {
        if (mode != RuntimeMode.ROOT_MODULE) {
            return ProviderDebugResolution(false, "disabled:mode-${mode.name.lowercase()}")
        }
        if (rootSource == null) {
            return ProviderDebugResolution(false, "disabled:source-unavailable")
        }
        val raw = runCatching { rootSource.read(provider) }
            .getOrElse { error ->
                return ProviderDebugResolution(
                    false,
                    "disabled:read-${error.javaClass.simpleName}"
                )
            }
        return when (raw) {
            true -> ProviderDebugResolution(true, "enabled")
            false -> ProviderDebugResolution(false, "disabled")
            null -> ProviderDebugResolution(false, "disabled:source-unavailable")
        }
    }

    fun configureDiagnostics(
        mode: RuntimeMode,
        provider: ProviderId,
        rootSource: ProviderDebugSource? = null
    ): Boolean = applyDiagnostics(mode, provider, rootSource, frameworkSink = null).enabled

    fun applyDiagnostics(
        mode: RuntimeMode,
        provider: ProviderId,
        rootSource: ProviderDebugSource? = null,
        frameworkSink: DiagnosticSink? = null
    ): ProviderDebugResolution {
        val resolution = resolveDetailed(mode, provider, rootSource)
        StructuredDiagnostics.configureForRuntime(mode, resolution.enabled, frameworkSink)
        return resolution
    }
}
