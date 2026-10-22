/*
 * Copyright 2026 Proify, Tomakino, Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.core.mode

import android.content.Context
import android.os.Build
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics

/**
 * Resolves whether the LSPosed module entry is active, strictly once per process.
 * Fails closed when the entry has not been observed.
 */
object RuntimeModeResolver {

    @Volatile
    private var cachedResolution: RuntimeModeResolution? = null

    @Volatile
    private var isXposedActive: Boolean = false

    fun notifyXposedHookActive() {
        isXposedActive = true
    }

    @Synchronized
    fun resolve(context: Context?): RuntimeModeResolution {
        cachedResolution?.let { return it }

        val hostPkg = context?.packageName ?: "unknown.host"
        val procName = getProcessName(context)

        val resolution = evaluateSignals(
            RuntimeModeSignals(
                hostPackage = hostPkg,
                processName = procName,
                xposedActive = isXposedActive
            )
        )
        cachedResolution = resolution

        if (resolution.mode == RuntimeMode.UNKNOWN) {
            StructuredDiagnostics.logWarning(
                DiagnosticEvent(
                    component = "provider/core",
                    area = "bootstrap",
                    event = "RUNTIME_MODE_UNKNOWN",
                    mode = resolution.mode,
                    process = procName,
                    reason = resolution.markerSource,
                    message = "Provider transport is disabled."
                )
            )
        } else {
            StructuredDiagnostics.logInfo(
                DiagnosticEvent(
                    component = "provider/core",
                    area = "bootstrap",
                    event = "RUNTIME_MODE_RESOLVED",
                    mode = resolution.mode,
                    process = procName,
                    reason = resolution.markerSource
                )
            )
        }

        return resolution
    }

    fun currentResolution(): RuntimeModeResolution? = cachedResolution

    internal fun evaluateSignals(signals: RuntimeModeSignals): RuntimeModeResolution {
        if (signals.xposedActive) {
            return RuntimeModeResolution(
                mode = RuntimeMode.ROOT_MODULE,
                hostPackage = signals.hostPackage,
                processName = signals.processName,
                markerSource = "xposed:hook-active"
            )
        }

        return RuntimeModeResolution(
            mode = RuntimeMode.UNKNOWN,
            hostPackage = signals.hostPackage,
            processName = signals.processName,
            markerSource = "none"
        )
    }

    private fun getProcessName(context: Context?): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val appProcessName = android.app.Application.getProcessName()
            if (!appProcessName.isNullOrBlank()) return appProcessName
        }
        return context?.packageName ?: "unknown.process"
    }

    internal fun resetForTesting() {
        cachedResolution = null
        isXposedActive = false
    }
}

internal data class RuntimeModeSignals(
    val hostPackage: String,
    val processName: String,
    val xposedActive: Boolean = false
)
