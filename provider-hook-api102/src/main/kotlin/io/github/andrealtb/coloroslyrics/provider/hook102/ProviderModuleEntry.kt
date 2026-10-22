/*
 * Copyright 2026 Andrea-TB
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.hook102

import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticEvent
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.StructuredDiagnostics
import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeModeResolver
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Base class for the single libxposed API 102 entry of each v4.1 Provider.
 *
 * Responsibilities:
 * 1. mark the module active on load and emit MODULE_LOADED to logcat plus the framework log;
 * 2. gate by package and process, then hook Application.attach for a one-time in-process
 *    bootstrap on the first accepted package;
 * 3. detach before any business hook is installed for rejected packages or processes.
 *
 * Subclasses must keep a public no-argument constructor and be listed as the only line of the
 * module's META-INF/xposed/java_init.list.
 */
abstract class ProviderModuleEntry : XposedModule() {

    protected abstract val providerId: ProviderId

    /** Structured log component field, for example "provider/salt". */
    protected abstract val diagnosticsComponent: String

    protected abstract val processPolicy: ProviderProcessPolicy

    protected abstract fun createHookInstaller(): ProviderHookInstaller

    private val frameworkSink by lazy { FrameworkLogSink(this) }
    private val runtime by lazy { Api102HookRuntime(this, providerId.configKey) }
    private val bootstrap by lazy {
        ProviderApplicationBootstrap(
            entry = this,
            runtime = runtime,
            providerId = providerId,
            diagnosticsComponent = diagnosticsComponent,
            frameworkSink = frameworkSink,
            installer = createHookInstaller()
        )
    }

    final override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        RuntimeModeResolver.notifyXposedHookActive()
        StructuredDiagnostics.addSink(frameworkSink)
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = diagnosticsComponent,
                area = "bootstrap",
                event = "MODULE_LOADED",
                process = param.processName,
                reason = "framework=${frameworkName} api=${apiVersion}"
            )
        )
    }

    final override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        val processName = ProviderProcessNames.currentProcessName()
        if (!processPolicy.accepts(packageName, processName)) {
            StructuredDiagnostics.logInfo(
                DiagnosticEvent(
                    component = diagnosticsComponent,
                    area = "bootstrap",
                    event = "PROCESS_SKIPPED",
                    process = processName.ifBlank { packageName },
                    reason = "policy=${processPolicy.describe(packageName, processName)} package=$packageName"
                )
            )
            runCatching { detach() }
            return
        }
        StructuredDiagnostics.logInfo(
            DiagnosticEvent(
                component = diagnosticsComponent,
                area = "bootstrap",
                event = "PROCESS_ACCEPTED",
                process = processName,
                reason = "policy=${processPolicy.describe(packageName, processName)} package=$packageName"
            )
        )
        bootstrap.schedule(param, processName)
    }
}

/** Installs all business hooks for one accepted host process. */
fun interface ProviderHookInstaller {
    fun install(context: ProviderHookContext)
}
